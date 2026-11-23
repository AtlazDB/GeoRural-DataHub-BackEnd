package geoRural.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.bmc.objectstorage.ObjectStorage;

import geoRural.dto.ComparacaoImovelResponse;
import geoRural.dto.OrigemImovelResponse;
import geoRural.dto.VersaoFonteResponse;
import geoRural.exception.ImovelNaoEncontradoException;
import geoRural.exception.RequisicaoInvalidaException;

/**
 * Contra o Oracle do docker-compose, desfeito no fim (@Transactional). Simula duas cargas do CAR
 * como a DAG faz: só regrava o imóvel cujo hash mudou, e o trigger da V25 guarda a versão anterior.
 */
@SpringBootTest
@Transactional
class VersaoServiceTest {

    @MockitoBean
    private ObjectStorage objectStorage;

    @MockitoBean
    private AirflowClient airflow;

    @Autowired
    private VersaoService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long fonteId;
    private long v1;
    private long v2;

    @BeforeEach
    void duasCargas() {
        jdbc.update("INSERT INTO fonte (nome, sigla, orgao, formato) VALUES ('CAR de teste das versões', 'TESTE_V', 'Teste', 'SHP')");
        fonteId = jdbc.queryForObject("SELECT id FROM fonte WHERE sigla = 'TESTE_V'", Long.class);
        v1 = versao("teste/versoes/car_v1.zip", "hash-da-carga-1");
        v2 = versao("teste/versoes/car_v2.zip", "hash-da-carga-2");

        imovel("TESTE-V-1", v1, "12.5", "AT", "a1", -40.00);
        imovel("TESTE-V-2", v1, "20", "AT", "a2", -40.10);
        imovel("TESTE-V-3", v1, "8", "PE", "a3", -40.20);

        // Segunda carga: V-1 mudou de área e de contorno, V-3 sumiu da fonte e V-4 é novo.
        jdbc.update("UPDATE imovel_rural SET area_ha = 13.1, hash_registro = 'b1', dataset_versao_id = ?, geometria = "
                + quadrado(-40.00, 0.02) + " WHERE cod_imovel = 'TESTE-V-1'", v2);
        jdbc.update("DELETE FROM imovel_rural WHERE cod_imovel = 'TESTE-V-3'");
        imovel("TESTE-V-4", v2, "5", "AT", "a4", -40.30);
    }

    @Test
    void triggerGuardaSoOQueMudouOuSaiu() {
        List<Map<String, Object>> historico = jdbc.queryForList("""
                SELECT cod_imovel, operacao, dataset_versao_id, substituida_por_versao_id,
                       JSON_VALUE(atributos, '$.area_ha' RETURNING NUMBER) AS area
                  FROM imovel_rural_historico WHERE cod_imovel LIKE 'TESTE-V-%' ORDER BY cod_imovel""");

        assertThat(historico).extracting(h -> h.get("COD_IMOVEL"), h -> h.get("OPERACAO"))
                .containsExactly(tuple("TESTE-V-1", "ALTERADO"), tuple("TESTE-V-3", "REMOVIDO"));
        assertThat(historico).allSatisfy(h -> {
            assertThat(((Number) h.get("DATASET_VERSAO_ID")).longValue()).isEqualTo(v1);
            assertThat(((Number) h.get("SUBSTITUIDA_POR_VERSAO_ID")).longValue()).isEqualTo(v2);
        });
        assertThat((BigDecimal) historico.get(0).get("AREA")).isEqualByComparingTo("12.5");
    }

    @Test
    void regravarNaMesmaVersaoNaoViraVersaoNova() {
        jdbc.update("UPDATE imovel_rural SET situacao = 'CA', hash_registro = 'a2x' WHERE cod_imovel = 'TESTE-V-2'");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM imovel_rural_historico WHERE cod_imovel = 'TESTE-V-2'", Integer.class)).isZero();
    }

    @Test
    void historicoNaoPodeSerAlterado() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE imovel_rural_historico SET hash_registro = 'x' WHERE cod_imovel = 'TESTE-V-1'"))
                .hasMessageContaining("imutável");
    }

    @Test
    void apagarUmaVersaoLevaOHistoricoDelaJunto() {
        jdbc.update("DELETE FROM imovel_rural WHERE dataset_versao_id = ?", v2);
        jdbc.update("DELETE FROM dataset_versao WHERE id = ?", v2);

        List<Map<String, Object>> restante = jdbc.queryForList("""
                SELECT cod_imovel, dataset_versao_id, substituida_por_versao_id
                  FROM imovel_rural_historico WHERE cod_imovel LIKE 'TESTE-V-%' ORDER BY cod_imovel""");
        assertThat(restante).extracting(h -> h.get("COD_IMOVEL")).containsExactly("TESTE-V-1", "TESTE-V-3");
        assertThat(restante).allSatisfy(h -> assertThat(h.get("SUBSTITUIDA_POR_VERSAO_ID")).isNull());
    }

    @Test
    void versoesDaFonteContamNovosAlteradosERemovidos() {
        List<VersaoFonteResponse> versoes = service.versoesDaFonte(fonteId);

        assertThat(versoes).extracting(VersaoFonteResponse::id, VersaoFonteResponse::atual,
                        VersaoFonteResponse::novos, VersaoFonteResponse::alterados, VersaoFonteResponse::removidos,
                        VersaoFonteResponse::hash)
                .containsExactly(
                        tuple(v2, true, 1, 1, 1, "hash-da-carga-2"),
                        tuple(v1, false, 3, 0, 0, "hash-da-carga-1"));
    }

    @Test
    void origemTrazATrilhaEAsVersoesDoImovel() {
        OrigemImovelResponse origem = service.origem("TESTE-V-1");

        assertThat(origem.versaoAtual().datasetVersaoId()).isEqualTo(v2);
        assertThat(origem.versaoAtual().arquivo()).isEqualTo("car_v2.zip");
        assertThat(origem.versaoAtual().hash()).isEqualTo("hash-da-carga-2");
        assertThat(origem.versaoAtual().fonte()).isEqualTo("TESTE_V");
        assertThat(origem.versoes()).extracting(v -> v.datasetVersaoId(), v -> v.situacao())
                .containsExactly(tuple(v2, "ATUAL"), tuple(v1, "ALTERADO"));
    }

    @Test
    void imovelRemovidoAindaTemOrigem() {
        OrigemImovelResponse origem = service.origem("TESTE-V-3");

        assertThat(origem.versaoAtual()).isNull();
        assertThat(origem.versoes()).extracting(v -> v.situacao()).containsExactly("REMOVIDO");
    }

    @Test
    void compararMostraOsCamposEOMotivoDoIndicador() {
        indicador("TESTE-V-1", v1, regraIrl(1), "18.2");
        indicador("TESTE-V-1", v2, regraIrl(1), "17.9");

        ComparacaoImovelResponse comparacao = service.comparar("TESTE-V-1", null, null);

        assertThat(comparacao.de().datasetVersaoId()).isEqualTo(v1);
        assertThat(comparacao.para().datasetVersaoId()).isEqualTo(v2);
        assertThat(comparacao.campos()).filteredOn(ComparacaoImovelResponse.Campo::mudou)
                .extracting(ComparacaoImovelResponse.Campo::campo).containsExactly("area_ha");
        assertThat(comparacao.geometriaMudou()).isTrue();
        assertThat(comparacao.areaGeometriaParaHa()).isGreaterThan(comparacao.areaGeometriaDeHa());
        assertThat(comparacao.indicadores()).singleElement().satisfies(i -> {
            assertThat(i.valorDe()).isEqualByComparingTo("18.2");
            assertThat(i.valorPara()).isEqualByComparingTo("17.9");
            assertThat(i.motivo()).isEqualTo("DADO");
        });
    }

    @Test
    void indicadorComARegraTrocadaDizQueAsDuasCoisasMudaram() {
        indicador("TESTE-V-1", v1, regraIrl(1), "18.2");
        indicador("TESTE-V-1", v2, regraIrl(2), "17.4");

        ComparacaoImovelResponse.Indicador irl = service.comparar("TESTE-V-1", v1, v2).indicadores().get(0);

        assertThat(irl.regraDe()).endsWith("v1");
        assertThat(irl.regraPara()).endsWith("v2");
        assertThat(irl.motivo()).isEqualTo("DADO_E_REGRA");
    }

    @Test
    void primeiraVersaoNaoTemComQueComparar() {
        ComparacaoImovelResponse comparacao = service.comparar("TESTE-V-4", null, null);

        assertThat(comparacao.de()).isNull();
        assertThat(comparacao.campos()).isEmpty();
    }

    @Test
    void versaoQueNaoEDoImovelEhRecusada() {
        assertThatThrownBy(() -> service.comparar("TESTE-V-4", v1, v2)).isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> service.origem("NAO-EXISTE")).isInstanceOf(ImovelNaoEncontradoException.class);
    }

    private long versao(String chave, String hash) {
        jdbc.update("""
                INSERT INTO arquivo_bruto (fonte_id, chave_objeto, nome_original, tamanho_bytes, hash_sha256)
                VALUES (?, ?, SUBSTR(?, INSTR(?, '/', -1) + 1), 1, ?)""", fonteId, chave, chave, chave, hash);
        jdbc.update("""
                INSERT INTO execucao (arquivo_bruto_id, tipo)
                SELECT id, 'PUBLICACAO' FROM arquivo_bruto WHERE chave_objeto = ?""", chave);
        jdbc.update("""
                INSERT INTO dataset_versao (conjunto, versao, execucao_id, zona)
                SELECT 'imoveis_rurais', (SELECT NVL(MAX(versao), 0) + 1 FROM dataset_versao WHERE conjunto = 'imoveis_rurais'),
                       e.id, 'TRATADA'
                  FROM execucao e JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
                 WHERE a.chave_objeto = ?""", chave);
        return jdbc.queryForObject("""
                SELECT dv.id FROM dataset_versao dv
                  JOIN execucao e ON e.id = dv.execucao_id
                  JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
                 WHERE a.chave_objeto = ?""", Long.class, chave);
    }

    private void imovel(String cod, long versao, String area, String situacao, String hash, double lon) {
        jdbc.update("INSERT INTO imovel_rural (cod_imovel, municipio, estado, area_ha, situacao, hash_registro, dataset_versao_id, geometria)"
                + " VALUES (?, 'Teste', 'PR', ?, ?, ?, ?, " + quadrado(lon, 0.01) + ")",
                cod, new BigDecimal(area), situacao, hash, versao);
    }

    // Em alto-mar, longe de qualquer imóvel de verdade; anel no sentido anti-horário, como o Oracle exige.
    private static String quadrado(double lon, double lado) {
        double lat = -30.0;
        return String.format(java.util.Locale.ROOT,
                "SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1), "
                        + "SDO_ORDINATE_ARRAY(%f, %f, %f, %f, %f, %f, %f, %f, %f, %f))",
                lon, lat, lon + lado, lat, lon + lado, lat + lado, lon, lat + lado, lon, lat);
    }

    private long regraIrl(int versao) {
        if (versao > 1) {
            jdbc.update("""
                    INSERT INTO regra_calculo (indicador, versao, codigo, nome, unidade, formula, descricao, parametros, ativa, vigente_desde)
                    SELECT 'IRL', ?, 'RN-IRL-Teste', 'Reserva Legal', '%', 'teste', 'teste', '{}', 0, SYSTIMESTAMP FROM dual
                     WHERE NOT EXISTS (SELECT 1 FROM regra_calculo WHERE indicador = 'IRL' AND versao = ?)""", versao, versao);
        }
        return jdbc.queryForObject("SELECT id FROM regra_calculo WHERE indicador = 'IRL' AND versao = ?", Long.class, versao);
    }

    private void indicador(String cod, long versaoImovel, long regraId, String valor) {
        jdbc.update("""
                INSERT INTO indicador_imovel (cod_imovel, indicador, valor, unidade, status, regra_calculo_id,
                                              dataset_versao_id, versao_imovel_id, calculado_em)
                VALUES (?, 'IRL', ?, '%', 'CRITICO', ?, ?, ?, SYSTIMESTAMP)""",
                cod, new BigDecimal(valor), regraId, versaoImovel, versaoImovel);
    }
}
