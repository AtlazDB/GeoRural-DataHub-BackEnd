package geoRural.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import geoRural.dto.*;
import geoRural.entity.RegistroQuarentena;
import geoRural.exception.GravacaoNaoPermitidaException;
import geoRural.exception.QuarantineClosedException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.repository.RegistroQuarentenaRepository;
import jakarta.persistence.EntityManager;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuarentenaService {

    // Status em que a análise já acabou: nenhuma ação muda mais o registro.
    private static final Set<String> STATUS_ENCERRADOS = Set.of("DESCARTADO", "REVALIDADO", "SUBSTITUIDO");

    // A DAG do Airflow que revalida um registro, com as mesmas regras da carga.
    private static final String DAG_REVALIDAR = "revalidar_registro";
    private static final String TAREFA_REVALIDAR = "revalidar";
    private static final String STATUS_RECUSADO = "RECUSADO";

    // Quanto a área pode encolher numa correção automática (a mesma trava da carga, regras.py).
    private static final double VARIACAO_AREA_CORRECAO = 0.01;

    private final RegistroQuarentenaRepository repository;
    private final ObjectMapper objectMapper;
    private final GeoJsonService geoJsonService;
    private final EntityManager entityManager;
    private final AirflowClient airflow;
    private final TransactionTemplate transacao;

    public QuarentenaService(RegistroQuarentenaRepository repository,
                             ObjectMapper objectMapper, GeoJsonService geoJsonService,
                             EntityManager entityManager, AirflowClient airflow,
                             TransactionTemplate transacao) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.geoJsonService = geoJsonService;
        this.entityManager = entityManager;
        this.airflow = airflow;
        this.transacao = transacao;
    }

    public Page<RegistroQuarentena> listar(QuarentenaFiltroDTO filtro, Pageable pageable) {
        return repository.buscarComFiltros(
            filtro.tipoErro(),
            filtro.status(),
            filtro.texto(),
            filtro.fonteId(),
            pageable
        );
    }

    public RegistroQuarentena buscarPorId(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Registro de quarentena não encontrado: " + id));
    }

    /** A geometria como chegou, em GeoJSON, para o visualizador. Null se ela nem pôde ser lida. */
    public Map<String, Object> buscarGeometria(Long id) {
        return geoJsonService.converter(buscarPorId(id).getGeometria());
    }

    /** A geometria que o operador salvou, em GeoJSON. Null enquanto não houver correção. */
    public Map<String, Object> findCorrectedGeometry(Long id) {
        return geoJsonService.converter(buscarPorId(id).getGeometriaCorrigida());
    }

    /**
     * Sugestão da correção automática em GeoJSON, com o JTS (mesmo padrão OGC do Shapely, que julga
     * na carga). Não grava: a tela mostra a prévia e o operador salva se concordar.
     */
    public Map<String, Object> suggestGeometryFix(Long id) {
        Geometry original = buscarEmAberto(id).getGeometria();
        if (original == null) {
            throw new RequisicaoInvalidaException(
                    "O registro não tem geometria legível para corrigir automaticamente: corrija pelo GeoJSON ou descarte com justificativa.",
                    List.of("geometria"));
        }
        if (original.isValid()) {
            throw new RequisicaoInvalidaException(
                    "A geometria original já é válida: não há o que corrigir automaticamente.", List.of("geometria"));
        }

        Geometry corrigida = GeometryFixer.fix(original);
        if (corrigida.isEmpty() || !corrigida.isValid() || corrigida.getDimension() != original.getDimension()
                || perdeuAreaDemais(original, corrigida)) {
            throw new RequisicaoInvalidaException(
                    "A correção automática não conseguiu deixar a geometria válida: edite os vértices no mapa.",
                    List.of("geometria"));
        }
        return geoJsonService.converter(corrigida);
    }

    private static boolean perdeuAreaDemais(Geometry antes, Geometry depois) {
        if (antes.getDimension() != 2) {
            return false;
        }
        return depois.getArea() <= 0 || (antes.getArea() > 0 && depois.getArea() < antes.getArea() * (1 - VARIACAO_AREA_CORRECAO));
    }

    /**
     * Grava a geometria corrigida (editada à mão ou a sugestão automática). Só aceita se for
     * válida no padrão OGC (JTS), o mesmo do Shapely, que julga no "Revalidar". Como o payload
     * corrigido, deixa o registro CORRIGIDO, pronto para revalidar.
     */
    @Transactional
    public RegistroQuarentena saveCorrectedGeometry(Long id, GeometryCorrectionRequest dto) {
        RegistroQuarentena registro = buscarEmAberto(id);

        Geometry geometria;
        try {
            geometria = geoJsonService.ler(objectMapper.writeValueAsString(dto.geometry()));
        } catch (ParseException | JsonProcessingException | RuntimeException e) {
            geometria = null;
        }
        // O JTS lê {"type": "Polygon"} sem coordenadas como polígono vazio.
        if (geometria == null || geometria.isEmpty()) {
            throw new RequisicaoInvalidaException("A geometria corrigida não é um GeoJSON válido.",
                    List.of("geometria"));
        }

        TopologyValidationError erro = new IsValidOp(geometria).getValidationError();
        if (erro != null) {
            throw new RequisicaoInvalidaException(
                    "A geometria corrigida ainda é inválida: " + descreverErro(erro), List.of("geometria"));
        }

        registro.setGeometriaCorrigida(geometria);
        registro.setStatusAnalise("CORRIGIDO");
        if (dto.justification() != null && !dto.justification().isBlank()) {
            registro.setJustificativa(dto.justification());
        }
        registro.setAnalisadoEm(OffsetDateTime.now());
        return repository.save(registro);
    }

    /** O erro de topologia em texto para a tela, com o ponto [x y], que o mapa marca em vermelho. */
    static String descreverErro(TopologyValidationError erro) {
        String texto = switch (erro.getErrorType()) {
            case TopologyValidationError.SELF_INTERSECTION, TopologyValidationError.RING_SELF_INTERSECTION
                    -> "o contorno do polígono se cruza";
            case TopologyValidationError.RING_NOT_CLOSED -> "o contorno do polígono não fecha";
            case TopologyValidationError.TOO_FEW_POINTS -> "o polígono tem menos de 3 vértices";
            case TopologyValidationError.NESTED_HOLES, TopologyValidationError.DISCONNECTED_INTERIOR,
                 TopologyValidationError.HOLE_OUTSIDE_SHELL, TopologyValidationError.NESTED_SHELLS,
                 TopologyValidationError.DUPLICATE_RINGS -> "anéis do polígono se tocam ou se sobrepõem";
            default -> "erro de geometria: " + erro.getMessage();
        };
        return erro.getCoordinate() == null ? texto + "."
                : texto + " [" + erro.getCoordinate().x + " " + erro.getCoordinate().y + "].";
    }

    private RegistroQuarentena buscarEmAberto(Long id) {
        RegistroQuarentena registro = buscarPorId(id);
        if (STATUS_ENCERRADOS.contains(registro.getStatusAnalise())) {
            throw new QuarantineClosedException(id, registro.getStatusAnalise());
        }
        return registro;
    }

    // O "Revalidar" lê cada campo do payload pelo nome: ele precisa ser um objeto JSON.
    private void conferirJson(String payload) {
        JsonNode json;
        try {
            json = payload == null ? null : objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            json = null;
        }
        if (json == null || !json.isObject()) {
            throw new RequisicaoInvalidaException("O payload corrigido precisa ser um objeto JSON.",
                    List.of("payloadCorrigido"));
        }
    }

    public QuarentenaMetricasDTO obterMetricas() {
        long pendentes = repository.countByStatusAnalise("PENDENTE");
        long emAnalise = repository.countByStatusAnalise("EM_ANALISE");
        long corrigidos = repository.countByStatusAnalise("CORRIGIDO");
        long descartados = repository.countByStatusAnalise("DESCARTADO");
        long revalidados = repository.countByStatusAnalise("REVALIDADO");
        long substituidos = repository.countByStatusAnalise("SUBSTITUIDO");
        long total = repository.count();

        return new QuarentenaMetricasDTO(pendentes, emAnalise, corrigidos, descartados, revalidados,
                substituidos, total);
    }

    @Transactional
    public RegistroQuarentena iniciarAnalise(Long id) {
        RegistroQuarentena registro = buscarEmAberto(id);
        registro.setStatusAnalise("EM_ANALISE");
        return repository.save(registro);
    }

    @Transactional
    public RegistroQuarentena descartar(Long id, DescarteRequestDTO dto) {
        RegistroQuarentena registro = buscarEmAberto(id);
        registro.setStatusAnalise("DESCARTADO");
        registro.setJustificativa(dto.justificativa());
        registro.setAnalisadoEm(OffsetDateTime.now());
        return repository.save(registro);
    }

    @Transactional
    public RegistroQuarentena corrigir(Long id, CorrecaoRequestDTO dto) {
        RegistroQuarentena registro = buscarEmAberto(id);
        conferirJson(dto.payloadCorrigido());
        registro.setStatusAnalise("CORRIGIDO");
        registro.setPayloadCorrigido(dto.payloadCorrigido());
        if (dto.justificativa() != null) {
            registro.setJustificativa(dto.justificativa());
        }
        registro.setAnalisadoEm(OffsetDateTime.now());
        return repository.save(registro);
    }

    /**
     * Revalida pela DAG do Airflow, que usa as mesmas regras da carga e, se passar, grava na Zona
     * Tratada e encerra o registro. Sem transação aqui: a DAG trava o registro na conexão dela, e
     * o back-end só espera a resposta (uns 3 segundos). Reprovado: continua na quarentena como
     * está, e a tela mostra os erros. Recusado (carga mais nova, sem versão...): 409 com o motivo.
     */
    public RevalidationResponse revalidar(Long id) {
        buscarEmAberto(id);
        JsonNode resultado = airflow.executar(DAG_REVALIDAR, TAREFA_REVALIDAR, Map.of("quarentena_id", id));

        String status = resultado.path("status").asText();
        if (STATUS_RECUSADO.equals(status)) {
            throw new GravacaoNaoPermitidaException(resultado.path("mensagem").asText());
        }
        List<RevalidationError> erros = objectMapper.convertValue(resultado.path("erros"), new TypeReference<>() {});
        // Relê o registro: quem gravou foi a DAG, em outra conexão.
        RegistroQuarentena registro = transacao.execute(t -> {
            RegistroQuarentena atual = buscarPorId(id);
            entityManager.refresh(atual);
            return atual;
        });
        return new RevalidationResponse(status, erros, registro);
    }
}
