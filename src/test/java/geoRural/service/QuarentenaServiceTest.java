package geoRural.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.objectstorage.ObjectStorage;
import geoRural.dto.CorrecaoRequestDTO;
import geoRural.dto.DescarteRequestDTO;
import geoRural.dto.GeometryCorrectionRequest;
import geoRural.dto.QuarentenaFiltroDTO;
import geoRural.dto.RevalidationError;
import geoRural.dto.RevalidationResponse;
import geoRural.exception.GravacaoNaoPermitidaException;
import geoRural.exception.QuarantineClosedException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.RegistroQuarentenaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contra o Oracle do docker-compose. Cada teste é desfeito no fim (@Transactional). O "Revalidar"
 * mesmo roda no Airflow (georural/revalidacao.py, com testes lá): aqui, a resposta da DAG é simulada.
 */
@SpringBootTest
@Transactional
class QuarentenaServiceTest {

    private static final String ARQUIVO = "teste/quarentena/imoveis.zip";

    /** O cliente da OCI: evita exigir ~/.oci/config. */
    @MockitoBean
    private ObjectStorage objectStorage;

    @MockitoBean
    private AirflowClient airflow;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuarentenaService service;

    @Autowired
    private RegistroQuarentenaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long fonteId;
    private long execucaoId;

    /** Uma fonte com uma regra só: cod_imovel é obrigatório. */
    @BeforeEach
    void montarCenario() {
        jdbc.update("""
                INSERT INTO fonte (nome, sigla, orgao, formato)
                VALUES ('Fonte de teste da quarentena', 'TESTE_Q', 'Teste', 'CSV')""");
        fonteId = jdbc.queryForObject("SELECT id FROM fonte WHERE sigla = 'TESTE_Q'", Long.class);

        jdbc.update("INSERT INTO fonte_coluna (fonte_id, nome_coluna) VALUES (?, 'cod_imovel')", fonteId);
        jdbc.update("""
                INSERT INTO regra_campo
                  (fonte_id, fonte_coluna_id, destino_tabela, campo_destino, tipo_dado, obrigatorio, chave)
                SELECT ?, id, 'imovel_rural', 'cod_imovel', 'TEXTO', 1, 1
                  FROM fonte_coluna WHERE fonte_id = ?""", fonteId, fonteId);

        jdbc.update("""
                INSERT INTO arquivo_bruto (fonte_id, chave_objeto, nome_original, tamanho_bytes, hash_sha256)
                VALUES (?, ?, 'imoveis.zip', 1, 'teste')""", fonteId, ARQUIVO);
        jdbc.update("""
                INSERT INTO execucao (arquivo_bruto_id, tipo)
                SELECT id, 'VALIDACAO' FROM arquivo_bruto WHERE chave_objeto = ?""", ARQUIVO);
        execucaoId = jdbc.queryForObject("""
                SELECT e.id FROM execucao e
                  JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
                 WHERE a.chave_objeto = ?""", Long.class, ARQUIVO);
    }

    // ----- revalidar ------------------------------------------------------------

    @Test
    void revalidarDisparaADagEDevolveOsErrosDela() throws Exception {
        long id = quarentena("{\"area_ha\": \"12.5\"}");
        daDag("""
                {"status": "REPROVADO", "mensagem": null, "erros": [{"tipo_erro": "CAMPO_OBRIGATORIO_AUSENTE",
                 "campo": "cod_imovel", "motivo": "Campo obrigatório não informado.", "valor_recebido": null}]}""");

        RevalidationResponse resultado = service.revalidar(id);

        assertThat(resultado.status()).isEqualTo("REPROVADO");
        assertThat(resultado.errors()).extracting(RevalidationError::errorType)
                .containsExactly("CAMPO_OBRIGATORIO_AUSENTE");
        assertThat(resultado.errors().get(0).field()).isEqualTo("cod_imovel");
        assertThat(resultado.quarantine().getStatusAnalise()).isEqualTo("PENDENTE");
        verify(airflow).executar("revalidar_registro", "revalidar", Map.of("quarentena_id", id));
    }

    /** Aprovado: quem gravou foi a DAG, em outra conexão; o registro volta relido do banco. */
    @Test
    void revalidarAprovadoReleORegistro() throws Exception {
        long id = quarentena("{\"cod_imovel\": \"PR-TESTE-Q-001\"}");
        repository.flush();
        when(airflow.executar(eq("revalidar_registro"), eq("revalidar"), any())).thenAnswer(chamada -> {
            jdbc.update("UPDATE registro_quarentena SET status_analise = 'REVALIDADO' WHERE id = ?", id);
            return objectMapper.readTree("{\"status\": \"APROVADO\", \"erros\": [], \"mensagem\": null}");
        });

        RevalidationResponse resultado = service.revalidar(id);

        assertThat(resultado.approved()).isTrue();
        assertThat(resultado.errors()).isEmpty();
        assertThat(resultado.quarantine().getStatusAnalise()).isEqualTo("REVALIDADO");
    }

    /** Recusado pela DAG (sem versão, carga mais nova...): 409 com a mensagem dela. */
    @Test
    void revalidarRecusadoViraConflitoComAMensagem() throws Exception {
        long id = quarentena("{}");
        daDag("""
                {"status": "RECUSADO", "erros": [],
                 "mensagem": "A carga que trouxe o registro não criou versão de dados: não há versão para gravar."}""");

        assertThatThrownBy(() -> service.revalidar(id))
                .isInstanceOf(GravacaoNaoPermitidaException.class)
                .hasMessageStartingWith("A carga que trouxe o registro não criou versão de dados");
    }

    @Test
    void registroEncerradoNemChamaADag() {
        long id = quarentena("{}");
        service.descartar(id, new DescarteRequestDTO("Sem salvação."));
        repository.flush();

        assertThatThrownBy(() -> service.revalidar(id)).isInstanceOf(QuarantineClosedException.class);
        verifyNoInteractions(airflow);
    }

    private void daDag(String json) throws Exception {
        JsonNode resposta = objectMapper.readTree(json);
        when(airflow.executar(eq("revalidar_registro"), eq("revalidar"), any())).thenReturn(resposta);
    }

    @Test
    void corrigirGuardaOPayload() {
        long id = quarentena("{\"area_ha\": \"12.5\"}");

        var registro = service.corrigir(id, new CorrecaoRequestDTO("{\"cod_imovel\": \"PR-001\"}", null));

        assertThat(registro.getStatusAnalise()).isEqualTo("CORRIGIDO");
        assertThat(registro.getPayloadCorrigido()).isEqualTo("{\"cod_imovel\": \"PR-001\"}");
    }

    /** A staging exige um objeto JSON: texto solto ou lista não servem. */
    @Test
    void corrigirComPayloadQueNaoEObjetoJsonFalha() {
        long id = quarentena("{\"area_ha\": \"12.5\"}");

        assertThatThrownBy(() -> service.corrigir(id, new CorrecaoRequestDTO("não é json", null)))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> service.corrigir(id, new CorrecaoRequestDTO("[1, 2]", null)))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    // ----- status ---------------------------------------------------------------

    @Test
    void registroDescartadoNaoAceitaMaisNada() {
        long id = quarentena("{\"area_ha\": \"12.5\"}");
        service.descartar(id, new DescarteRequestDTO("Registro de teste."));

        assertThatThrownBy(() -> service.revalidar(id)).isInstanceOf(QuarantineClosedException.class);
        assertThatThrownBy(() -> service.corrigir(id, new CorrecaoRequestDTO("{}", null)))
                .isInstanceOf(QuarantineClosedException.class);
        assertThatThrownBy(() -> service.iniciarAnalise(id)).isInstanceOf(QuarantineClosedException.class);
        assertThatThrownBy(() -> service.descartar(id, new DescarteRequestDTO("De novo.")))
                .isInstanceOf(QuarantineClosedException.class);
    }

    // ----- listagem e geometria -------------------------------------------------

    @Test
    void listarFiltraPelaFonte() {
        long id = quarentena("{\"area_ha\": \"12.5\"}");

        assertThat(service.listar(new QuarentenaFiltroDTO(null, fonteId, null, null), Pageable.unpaged()))
                .extracting(r -> r.getId()).containsExactly(id);
        assertThat(service.listar(new QuarentenaFiltroDTO(null, -1L, null, null), Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void geometriaVemEmGeoJson() {
        jdbc.update("""
                INSERT INTO registro_quarentena (execucao_id, numero_linha, motivo, payload, geometria)
                VALUES (?, 1, 'teste da quarentena', '{}', SDO_GEOMETRY(2003, 4326, NULL,
                          SDO_ELEM_INFO_ARRAY(1, 1003, 1),
                          SDO_ORDINATE_ARRAY(-51.20, -23.35, -51.15, -23.35, -51.15, -23.30,
                                             -51.20, -23.30, -51.20, -23.35)))""", execucaoId);
        long comGeometria = ultimaQuarentena();
        long semGeometria = quarentena("{}");

        Map<String, Object> geoJson = service.buscarGeometria(comGeometria);

        assertThat(geoJson).containsEntry("type", "Polygon");
        assertThat(service.buscarGeometria(semGeometria)).isNull();
    }

    // ----- correção da geometria ------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void correcaoAutomaticaSugereGeometriaValidaSemGravar() {
        long id = quarentenaComGeometria("{}", BORBOLETA);

        Map<String, Object> sugestao = service.suggestGeometryFix(id);

        assertThat((String) sugestao.get("type")).isIn("Polygon", "MultiPolygon");
        assertThat(service.findCorrectedGeometry(id)).isNull();
        assertThat(repository.findById(id).orElseThrow().getStatusAnalise()).isEqualTo("PENDENTE");
    }

    @Test
    void correcaoAutomaticaRecusaGeometriaValidaOuAusente() {
        long valida = quarentenaComGeometria("{}");
        long semGeometria = quarentena("{}");

        assertThatThrownBy(() -> service.suggestGeometryFix(valida))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("já é válida");
        assertThatThrownBy(() -> service.suggestGeometryFix(semGeometria))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("corrija pelo GeoJSON");
    }

    @Test
    void salvarGeometriaCorrigidaDeixaCorrigidoEPreservaAOriginal() {
        long id = quarentenaComGeometria("{}", BORBOLETA);

        var registro = service.saveCorrectedGeometry(id, new GeometryCorrectionRequest(QUADRADO, "Vértice trocado."));

        assertThat(registro.getStatusAnalise()).isEqualTo("CORRIGIDO");
        assertThat(registro.getJustificativa()).isEqualTo("Vértice trocado.");
        assertThat(service.findCorrectedGeometry(id)).containsEntry("type", "Polygon");
        assertThat(service.buscarGeometria(id)).isNotNull();
    }

    @Test
    void salvarGeometriaQueAindaSeCruzaFalha() {
        long id = quarentenaComGeometria("{}", BORBOLETA);
        Map<String, Object> borboleta = Map.of("type", "Polygon", "coordinates", List.of(List.of(
                List.of(-49.25, -25.42), List.of(-49.20, -25.42), List.of(-49.20, -25.48),
                List.of(-49.26, -25.40), List.of(-49.25, -25.42))));

        assertThatThrownBy(() -> service.saveCorrectedGeometry(id, new GeometryCorrectionRequest(borboleta, null)))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("se cruza");
        assertThatThrownBy(() -> service.saveCorrectedGeometry(id,
                new GeometryCorrectionRequest(Map.of("type", "Polygon"), null)))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("GeoJSON");
    }

    @Test
    void erroDeTopologiaViraTextoComOPontoParaOMapa() {
        var erro = new org.locationtech.jts.operation.valid.TopologyValidationError(
                org.locationtech.jts.operation.valid.TopologyValidationError.SELF_INTERSECTION,
                new org.locationtech.jts.geom.Coordinate(-49.25, -25.42));

        assertThat(QuarentenaService.descreverErro(erro)).isEqualTo("o contorno do polígono se cruza [-49.25 -25.42].");
    }

    // ----- montagem -------------------------------------------------------------

    /** Polígono em "laço" (as arestas se cruzam): inválido no JTS e no Shapely. */
    private static final String BORBOLETA = """
            SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
              SDO_ORDINATE_ARRAY(-49.25, -25.42, -49.20, -25.42, -49.20, -25.48, -49.26, -25.40, -49.25, -25.42))""";

    private static final Map<String, Object> QUADRADO = Map.of("type", "Polygon", "coordinates", List.of(List.of(
            List.of(-51.20, -23.35), List.of(-51.15, -23.35), List.of(-51.15, -23.30),
            List.of(-51.20, -23.30), List.of(-51.20, -23.35))));

    private long quarentena(String payload) {
        jdbc.update("""
                INSERT INTO registro_quarentena (execucao_id, numero_linha, motivo, payload)
                VALUES (?, 1, 'teste da quarentena', ?)""", execucaoId, payload);
        return ultimaQuarentena();
    }

    private long quarentenaComGeometria(String payload) {
        return quarentenaComGeometria(payload, """
                SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
                  SDO_ORDINATE_ARRAY(-51.20, -23.35, -51.15, -23.35, -51.15, -23.30,
                                     -51.20, -23.30, -51.20, -23.35))""");
    }

    private long quarentenaComGeometria(String payload, String geometriaSql) {
        jdbc.update("""
                INSERT INTO registro_quarentena (execucao_id, numero_linha, motivo, payload, geometria)
                VALUES (?, 1, 'teste da quarentena', ?, %s)""".formatted(geometriaSql), execucaoId, payload);
        return ultimaQuarentena();
    }

    private long ultimaQuarentena() {
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM registro_quarentena WHERE execucao_id = ?", Long.class, execucaoId);
    }
}
