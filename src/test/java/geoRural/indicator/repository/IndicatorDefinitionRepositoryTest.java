package geoRural.indicator.repository;

import com.oracle.bmc.objectstorage.ObjectStorage;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As consultas do cadastro de indicador, contra o Oracle do docker-compose.
 *
 * Fonte, arquivo, execução e versão próprios, com geometrias no meio do oceano (lon -35, lat -35),
 * para não se misturar com dados reais. Sem @Transactional de propósito, como no
 * ImovelRuralConsultaRepositoryTest: limpeza manual antes e depois.
 */
@SpringBootTest
class IndicatorDefinitionRepositoryTest {

    private static final String SIGLA_FONTE = "TESTE-IND";
    private static final String CHAVE = "bruta/TESTE-IND/teste/indicador.geojson";
    private static final String CODE = "TSTIND";

    @MockitoBean
    private ObjectStorage objectStorage;

    @Autowired
    private IndicatorDefinitionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long fonteId;
    private long datasetVersaoId;

    @BeforeEach
    void criar() {
        apagar();

        jdbc.update("""
                INSERT INTO fonte (nome, sigla, orgao, formato)
                VALUES ('Fonte de teste do indicador', ?, 'Teste', 'GeoJSON')
                """, SIGLA_FONTE);
        fonteId = jdbc.queryForObject("SELECT id FROM fonte WHERE sigla = ?", Long.class, SIGLA_FONTE);
        jdbc.update("""
                INSERT INTO arquivo_bruto (fonte_id, chave_objeto, nome_original, tamanho_bytes, hash_sha256, status)
                VALUES (?, ?, 'indicador.geojson', 1, 'x', 'PROCESSADO')
                """, fonteId, CHAVE);
        Long arquivo = jdbc.queryForObject("SELECT id FROM arquivo_bruto WHERE chave_objeto = ?", Long.class, CHAVE);
        jdbc.update("INSERT INTO execucao (arquivo_bruto_id, tipo, status) VALUES (?, 'VALIDACAO', 'CONCLUIDA')", arquivo);
        Long execucao = jdbc.queryForObject("SELECT id FROM execucao WHERE arquivo_bruto_id = ?", Long.class, arquivo);
        jdbc.update("INSERT INTO dataset_versao (conjunto, versao, execucao_id, zona) VALUES ('teste_indicador', 1, ?, 'TRATADA')",
                execucao);
        datasetVersaoId = jdbc.queryForObject("SELECT id FROM dataset_versao WHERE execucao_id = ?", Long.class, execucao);
    }

    @AfterEach
    void apagar() {
        jdbc.update("DELETE FROM indicator_definition WHERE code = ?", CODE);
        jdbc.update("""
                DELETE FROM input_feature WHERE dataset_versao_id IN (SELECT dv.id FROM dataset_versao dv
                  JOIN execucao e ON e.id = dv.execucao_id
                  JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id WHERE a.chave_objeto = ?)
                """, CHAVE);
        jdbc.update("""
                DELETE FROM dataset_versao WHERE execucao_id IN (SELECT e.id FROM execucao e
                  JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id WHERE a.chave_objeto = ?)
                """, CHAVE);
        jdbc.update("""
                DELETE FROM execucao WHERE arquivo_bruto_id IN (SELECT id FROM arquivo_bruto WHERE chave_objeto = ?)
                """, CHAVE);
        jdbc.update("DELETE FROM arquivo_bruto WHERE chave_objeto = ?", CHAVE);
        jdbc.update("DELETE FROM fonte WHERE sigla = ?", SIGLA_FONTE);
    }

    @Test
    void sourceWithoutDataHasNoGeometryTypes() {
        assertThat(repository.findGeometryTypesBySource(fonteId)).isEmpty();
    }

    @Test
    void findsTheGeometryTypesOfTheSource() {
        inserirPonto();
        inserirPonto();
        inserirPoligono();

        assertThat(repository.findGeometryTypesBySource(fonteId)).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void savesAndReadsBackWithParametersAndRangesAsJson() {
        assertThat(repository.existsByCode(CODE)).isFalse();

        repository.insert(new IndicatorDefinitionRequest(CODE, "Indicador de teste", "focos/km²", null,
                fonteId, "POINT_DENSITY", Map.of("factor", 100),
                List.of(new StatusRangeRequest("CONFORME", new BigDecimal("10.5"), true),
                        new StatusRangeRequest("CRITICO", null, null))));

        assertThat(repository.existsByCode(CODE)).isTrue();
        IndicatorDefinitionResponse lido = repository.findByCode(CODE).orElseThrow();
        assertThat(lido.fonteId()).isEqualTo(fonteId);
        assertThat(lido.parameters()).containsEntry("factor", 100);
        assertThat(lido.ranges()).containsExactly(
                new StatusRangeRequest("CONFORME", new BigDecimal("10.5"), true),
                new StatusRangeRequest("CRITICO", null, null));
        assertThat(lido.active()).isTrue();
        assertThat(lido.createdAt()).isNotNull();
        assertThat(repository.findAll()).extracting(IndicatorDefinitionResponse::code).contains(CODE);
    }

    @Test
    void savesEmptyParametersWhenNull() {
        repository.insert(new IndicatorDefinitionRequest(CODE, "Indicador de teste", "%", null,
                fonteId, "AREA_COVERAGE", null, List.of(new StatusRangeRequest("VALIDACAO", null, null))));

        assertThat(repository.findByCode(CODE).orElseThrow().parameters()).isEmpty();
    }

    @Test
    void deactivatesWithoutDeleting() {
        repository.insert(new IndicatorDefinitionRequest(CODE, "Indicador de teste", "%", null,
                fonteId, "AREA_COVERAGE", null, List.of(new StatusRangeRequest("VALIDACAO", null, null))));

        assertThat(repository.deactivate(CODE)).isTrue();
        assertThat(repository.findByCode(CODE).orElseThrow().active()).isFalse();
        assertThat(repository.deactivate("NAOEXISTE")).isFalse();
    }

    private void inserirPonto() {
        jdbc.update("""
                INSERT INTO input_feature (dataset_versao_id, geometria)
                VALUES (?, SDO_GEOMETRY(2001, 4326, SDO_POINT_TYPE(-35, -35, NULL), NULL, NULL))
                """, datasetVersaoId);
    }

    private void inserirPoligono() {
        jdbc.update("""
                INSERT INTO input_feature (dataset_versao_id, geometria)
                VALUES (?, SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
                        SDO_ORDINATE_ARRAY(-35, -35, -34.9, -35, -34.9, -34.9, -35, -34.9, -35, -35)))
                """, datasetVersaoId);
    }
}
