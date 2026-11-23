package geoRural.indicator.service;

import geoRural.acesso.AcessoTerritorial;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.indicator.dto.IndicatorCalculationResponse;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.IndicatorMunicipalityResponse;
import geoRural.indicator.dto.IndicatorResultResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import geoRural.indicator.exception.DuplicateIndicatorCodeException;
import geoRural.indicator.exception.IndicatorCalculationInProgressException;
import geoRural.indicator.repository.IndicatorCalculationRepository;
import geoRural.indicator.repository.IndicatorDefinitionRepository;
import geoRural.indicator.repository.IndicatorResultRepository;
import geoRural.repository.FonteRepository;
import geoRural.service.AirflowClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class IndicatorDefinitionServiceImpl implements  IndicatorDefinitionService {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");
    private static final String POINT_DENSITY = "POINT_DENSITY";
    private static final String AREA_COVERAGE = "AREA_COVERAGE";
    // IAPP: quanto da camada base (parameters.baseSources, ex.: APP) está coberto pela fonte (V26).
    private static final String BASE_COVERAGE = "BASE_COVERAGE";
    private static final Set<String> MODELS = Set.of(POINT_DENSITY, AREA_COVERAGE, BASE_COVERAGE);

    // get_gtype(): 1 = ponto, 5 = multiponto, 3 = polígono, 7 = multipolígono
    private static final Set<Integer> POINTS = Set.of(1, 5);
    private static final Set<Integer> POLYGONS = Set.of(3, 7);
    private static final Set<String> STATUSES = Set.of("CONFORME", "ALERTA", "CRITICO", "VALIDACAO");
    // O cálculo é do Airflow (GeoPandas): dags/calcular_indicador.py
    private static final String DAG_CALCULATE = "calcular_indicador";

    private final IndicatorDefinitionRepository repository;
    private final IndicatorResultRepository resultRepository;
    private final IndicatorCalculationRepository calculationRepository;
    private final FonteRepository fonteRepository;
    private final AirflowClient airflow;
    private final AcessoTerritorial acesso;

    public IndicatorDefinitionServiceImpl(IndicatorDefinitionRepository repository,
                                          IndicatorResultRepository resultRepository,
                                          IndicatorCalculationRepository calculationRepository,
                                          FonteRepository fonteRepository,
                                          AirflowClient airflow,
                                          AcessoTerritorial acesso) {
        this.repository = repository;
        this.resultRepository = resultRepository;
        this.calculationRepository = calculationRepository;
        this.fonteRepository = fonteRepository;
        this.airflow = airflow;
        this.acesso = acesso;
    }

    @Override
    @Transactional
    public IndicatorDefinitionResponse create(IndicatorDefinitionRequest request) {
        validateCode(request.code());
        validateRanges(request.ranges());
        validateModel(request.calculationModel());
        List<Long> sources = sources(request.fonteId(), request.parameters());
        for (int i = 0; i < sources.size(); i++) {
            // A primeira é a principal (fonteId); as outras vêm de parameters.extraSources.
            validateSource(sources.get(i), request.calculationModel(), i == 0 ? "fonteId" : "parameters");
        }
        validateSince(sources, request.parameters());
        validateBaseSources(request.calculationModel(), request.parameters());
        validateMinimumPercent(request.calculationModel(), request.parameters());
        repository.insert(request);
        return findByCode(request.code());
    }

    @Override
    public List<IndicatorDefinitionResponse> findAll() {
        return repository.findAll();
    }

    @Override
    public IndicatorDefinitionResponse findByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador \"" + code + "\" não encontrado."));
    }

    @Override
    @Transactional
    public void deactivate(String code) {
        if (!repository.deactivate(code)) {
            throw new ResourceNotFoundException("Indicador \"" + code + "\" não encontrado.");
        }
    }

    @Override
    @Transactional
    public void activate(String code) {
        if (!repository.activate(code)) {
            throw new ResourceNotFoundException("Indicador \"" + code + "\" não encontrado.");
        }
    }

    // Sem @Transactional de propósito: a linha NA_FILA precisa estar gravada (commit) antes de a DAG
    // começar, porque é nela que a DAG escreve o andamento.
    @Override
    public IndicatorCalculationResponse calculate(String code) {
        IndicatorDefinitionResponse indicator = findByCode(code);
        if (!indicator.active()) {
            throw new RequisicaoInvalidaException("O indicador \"" + code + "\" está desativado e não é mais calculado.");
        }
        closeStoppedCalculations();
        if (calculationRepository.findActive(code).isPresent()) {
            throw new IndicatorCalculationInProgressException(code);
        }
        long id = calculationRepository.create(code);
        try {
            // Os três modelos (POINT_DENSITY, AREA_COVERAGE e BASE_COVERAGE) são calculados pela mesma DAG.
            String dagRunId = airflow.disparar(DAG_CALCULATE, Map.of("code", code, "calculation_id", id));
            calculationRepository.setDagRunId(id, dagRunId);
        } catch (RuntimeException e) {
            // O disparo não chegou ao Airflow: sem isso, a linha ficaria NA_FILA para sempre e
            // bloquearia um novo "Calcular".
            calculationRepository.markError(id, e.getMessage());
            throw e;
        }
        return calculationRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("O cálculo " + id + " sumiu logo depois de criado."));
    }

    @Override
    public List<IndicatorCalculationResponse> findLatestCalculations() {
        closeStoppedCalculations();
        return calculationRepository.findLatestOfEach();
    }

    /**
     * Fecha como ERRO o cálculo ativo cuja execução no Airflow já acabou ou sumiu. Acontece quando a
     * tarefa morre sem passar pelo fim da DAG: execução parada pela API ou pela tela do Airflow,
     * falta de memória, Airflow reiniciado. Sem isso, o cálculo ficaria EM_ANDAMENTO para sempre e
     * o "Calcular" daria 409. Se o Airflow estiver fora do ar, não dá para saber: fica para a
     * próxima consulta (a tela pergunta a cada poucos segundos).
     */
    private void closeStoppedCalculations() {
        for (IndicatorCalculationRepository.ActiveRun run : calculationRepository.findActiveRuns()) {
            String state;
            try {
                state = airflow.estado(DAG_CALCULATE, run.dagRunId());
            } catch (AirflowIndisponivelException e) {
                return;
            }
            String message = stoppedMessage(state);
            if (message != null) {
                calculationRepository.markError(run.id(), message);
            }
        }
    }

    /** A mensagem de erro para a execução que acabou com o cálculo ainda ativo; null se ela segue viva. */
    private static String stoppedMessage(String state) {
        if (state == null) {
            return "A execução do cálculo não existe mais no Airflow. Calcule de novo.";
        }
        return switch (state) {
            case "failed" -> "O cálculo foi interrompido no Airflow. Calcule de novo.";
            // A DAG grava o fim antes de terminar; success com o cálculo ainda ativo = o fim não foi gravado.
            case "success" -> "O Airflow terminou sem registrar o fim do cálculo. Calcule de novo.";
            default -> null; // queued, running
        };
    }

    @Override
    public IndicatorResultResponse findLatestResult(String code, String codImovel) {
        // Primeiro o indicador: assim o 404 diz se o problema é a sigla ou o imóvel.
        findByCode(code);
        acesso.exigirImovel(codImovel);
        return resultRepository.findLatest(code, codImovel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "O indicador \"" + code + "\" ainda não foi calculado para o imóvel " + codImovel + "."));
    }

    @Override
    public List<IndicatorMunicipalityResponse> findByMunicipality(String code) {
        // Primeiro o indicador: sigla errada é 404; indicador ainda não calculado é lista vazia.
        findByCode(code);
        return resultRepository.findByMunicipality(code).stream()
                .filter(m -> acesso.permiteMunicipio(m.codIbge()))
                .toList();
    }

    private void validateCode(String code) {
        if (!CODE.matcher(code).matches()) {
            throw new RequisicaoInvalidaException(
                    "A sigla deve começar com letra e ter de 2 a 10 letras maiúsculas ou números.",
                    List.of("code"));
        }
        if (repository.existsByCode(code)) {
            throw new DuplicateIndicatorCodeException(code);
        }
    }

    private void validateModel(String calculationModel) {
        if (!MODELS.contains(calculationModel)) {
            throw new RequisicaoInvalidaException(
                    "Modelo de cálculo deve ser POINT_DENSITY, AREA_COVERAGE ou BASE_COVERAGE.",
                    List.of("calculationModel"));
        }
    }

    // A fonte principal e as de parameters.extraSources (ex.: ISAP = UC + TI + quilombola +
    // floresta pública), sem repetir. A DAG cruza o imóvel com a união de todas.
    private List<Long> sources(Long fonteId, Map<String, Object> parameters) {
        Set<Long> sources = new LinkedHashSet<>();
        sources.add(fonteId);
        sources.addAll(sourceIds(parameters, "extraSources"));
        return List.copyOf(sources);
    }

    // Uma lista de ids de fonte guardada no parameters (extraSources ou baseSources), sem repetir.
    private List<Long> sourceIds(Map<String, Object> parameters, String key) {
        Object value = parameters == null ? null : parameters.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new RequisicaoInvalidaException(
                    key + " deve ser uma lista de ids de fonte, ex.: [12, 15].", List.of("parameters"));
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Object item : list) {
            try {
                ids.add(Long.valueOf(item.toString()));
            } catch (NumberFormatException | NullPointerException e) {
                throw new RequisicaoInvalidaException(
                        key + " tem uma fonte inválida: " + item + ".", List.of("parameters"));
            }
        }
        return List.copyOf(ids);
    }

    // parameters.baseSources: a camada base do BASE_COVERAGE (ex.: a APP no IAPP), que vira o
    // denominador. Obrigatória nesse modelo, proibida nos outros (não seria usada).
    private void validateBaseSources(String calculationModel, Map<String, Object> parameters) {
        List<Long> base = sourceIds(parameters, "baseSources");
        if (!BASE_COVERAGE.equals(calculationModel)) {
            if (!base.isEmpty()) {
                throw new RequisicaoInvalidaException(
                        "baseSources só vale para o modelo BASE_COVERAGE.", List.of("parameters"));
            }
            return;
        }
        if (base.isEmpty()) {
            throw new RequisicaoInvalidaException(
                    "A cobertura dentro de uma camada exige a camada base (baseSources), ex.: a fonte da APP.",
                    List.of("parameters"));
        }
        for (Long fonteId : base) {
            validateSource(fonteId, calculationModel, "parameters");
        }
    }

    private void validateSource(Long fonteId, String calculationModel, String field) {
        Set<Integer> expected = calculationModel.equals(POINT_DENSITY) ? POINTS : POLYGONS;
        if (!fonteRepository.existsById(fonteId)) {
            throw new RequisicaoInvalidaException("A fonte " + fonteId + " não existe.", List.of(field));
        }
        List<Integer> types = repository.findGeometryTypesBySource(fonteId);
        if (types.isEmpty()) {
            throw new RequisicaoInvalidaException(
                    "A fonte " + fonteId + " ainda não tem dados processados no insumo genérico.", List.of(field));
        }
        if (!expected.containsAll(types)) {
            throw new RequisicaoInvalidaException(
                    calculationModel.equals(POINT_DENSITY)
                            ? "Densidade de pontos exige fontes só com pontos (a fonte " + fonteId + " tem outros tipos)."
                            : "Cobertura de área exige fontes só com polígonos (a fonte " + fonteId + " tem outros tipos).",
                    List.of("calculationModel"));
        }
    }

    /**
     * parameters.minimumPercent: o mínimo exigido, em % do imóvel (ex.: 20 para a Reserva Legal no
     * Paraná). Opcional e só na cobertura de área. A DAG não usa: com ele, a tela mostra o saldo em
     * hectares do imóvel (área coberta − mínimo × área do imóvel; negativo = déficit, positivo = excedente).
     */
    private void validateMinimumPercent(String calculationModel, Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("minimumPercent");
        if (value == null) {
            return;
        }
        if (!AREA_COVERAGE.equals(calculationModel)) {
            throw new RequisicaoInvalidaException(
                    "O mínimo exigido (minimumPercent) só vale para a cobertura de área.", List.of("parameters"));
        }
        BigDecimal minimum;
        try {
            minimum = new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            minimum = null;
        }
        if (minimum == null || minimum.signum() <= 0 || minimum.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RequisicaoInvalidaException(
                    "O mínimo exigido deve ser um número maior que 0 e até 100 (%), ex.: 20.", List.of("parameters"));
        }
    }

    private void validateSince(List<Long> sources, Map<String, Object> parameters) {
        Object since = parameters == null ? null : parameters.get("since");
        if (since == null || since.toString().isBlank()) {
            return;
        }
        try {
            LocalDate.parse(since.toString());
        } catch (DateTimeParseException e) {
            throw new RequisicaoInvalidaException(
                    "A data inicial (since) deve estar no formato AAAA-MM-DD, ex.: 2008-07-22.",
                    List.of("parameters"));
        }
        // Toda fonte do indicador: uma sem data seria cortada inteira pelo filtro, sem aviso.
        for (Long fonteId : sources) {
            if (!repository.hasReferenceDateBySource(fonteId)) {
                throw new RequisicaoInvalidaException(
                        "O filtro por data exige que a fonte " + fonteId + " tenha a data de referência preenchida "
                                + "(mapeie a coluna de data em data_referencia).",
                        List.of("parameters"));
            }
        }
    }

    private void validateRanges(List<StatusRangeRequest> ranges) {
        BigDecimal previous = null;
        for (int i = 0; i < ranges.size(); i++) {
            StatusRangeRequest range = ranges.get(i);
            boolean last = i == ranges.size() - 1;
            if (!STATUSES.contains(range.status())) {
                throw invalidRange(i, "o status deve ser CONFORME, ALERTA, CRITICO ou VALIDACAO.");
            }
            if (last) {
                if (range.limit() != null) {
                    throw invalidRange(i, "a última faixa não tem limite (vale para tudo acima da anterior).");
                }
            } else {
                if (range.limit() == null || range.includeLimit() == null) {
                    throw invalidRange(i, "só a última faixa pode ficar sem limit e includeLimit.");
                }
                if (previous != null && range.limit().compareTo(previous) <= 0) {
                    throw invalidRange(i, "o limite tem que ser maior que o da faixa anterior.");
                }
                previous = range.limit();
            }
        }
    }

    private RequisicaoInvalidaException invalidRange(int index, String message) {
        return new RequisicaoInvalidaException("Faixa " + (index + 1) + ": " + message, List.of("ranges"));
    }
}

