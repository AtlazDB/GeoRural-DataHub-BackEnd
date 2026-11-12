package geoRural.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import geoRural.dto.IndicadorCadastradoResultado;
import geoRural.dto.VersaoFonteResponse;
import geoRural.dto.VersaoImovelResponse;

/** Versões dos conjuntos de dados e do imóvel, com o histórico gravado pela V25. */
@Repository
public class VersaoRepository {

    private static final String CONJUNTO_IMOVEIS = "imoveis_rurais";

    private static final String TRILHA = """
              JOIN dataset_versao dv   ON dv.id = x.dataset_versao_id
              LEFT JOIN execucao e     ON e.id  = dv.execucao_id
              LEFT JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
              LEFT JOIN fonte f        ON f.id  = a.fonte_id
            """;

    private static final String SQL_VERSOES_IMOVEL = """
            SELECT x.situacao, dv.id, dv.conjunto, dv.versao,
                   COALESCE(dv.publicada_em, e.finalizada_em, e.iniciada_em) AS quando,
                   e.id AS execucao_id, a.id AS arquivo_id, a.nome_original, a.hash_sha256,
                   f.sigla, f.nome
              FROM (SELECT 'ATUAL' AS situacao, dataset_versao_id FROM imovel_rural WHERE cod_imovel = ?
                    UNION ALL
                    SELECT operacao, dataset_versao_id FROM imovel_rural_historico WHERE cod_imovel = ?) x
            """ + TRILHA + " ORDER BY dv.id DESC";

    // A mesma forma para a linha atual e para o histórico; a área é a do polígono, em hectares.
    private static final String SQL_RETRATO = """
            SELECT * FROM (
              SELECT 0 AS ordem, municipio, estado, area_ha, situacao, municipio_id, hash_registro,
                     SDO_GEOM.SDO_AREA(geometria, 0.05, 'unit=SQ_M') / 10000 AS area_geometria
                FROM imovel_rural WHERE cod_imovel = ? AND dataset_versao_id = ?
              UNION ALL
              SELECT 1, JSON_VALUE(atributos, '$.municipio'), JSON_VALUE(atributos, '$.estado'),
                     JSON_VALUE(atributos, '$.area_ha' RETURNING NUMBER), JSON_VALUE(atributos, '$.situacao'),
                     JSON_VALUE(atributos, '$.municipio_id' RETURNING NUMBER), hash_registro,
                     SDO_GEOM.SDO_AREA(geometria, 0.05, 'unit=SQ_M') / 10000
                FROM imovel_rural_historico WHERE cod_imovel = ? AND dataset_versao_id = ?
            ) ORDER BY ordem FETCH FIRST 1 ROWS ONLY
            """;

    private static final String SQL_GEOMETRIA_IGUAL = """
            WITH g AS (SELECT dataset_versao_id AS v, geometria FROM imovel_rural WHERE cod_imovel = ?
                       UNION ALL
                       SELECT dataset_versao_id, geometria FROM imovel_rural_historico WHERE cod_imovel = ?)
            SELECT SDO_GEOM.RELATE(a.geometria, 'EQUAL', b.geometria, 0.05)
              FROM g a JOIN g b ON a.v = ? AND b.v = ?
             FETCH FIRST 1 ROWS ONLY
            """;

    private static final String SQL_INDICADORES_CADASTRADOS = """
            SELECT * FROM (
              SELECT r.indicator_code, d.name, d.unit, r.value, r.status, r.dataset_versao_id,
                     r.versao_imovel_id, r.calculated_at,
                     ROW_NUMBER() OVER (PARTITION BY r.indicator_code ORDER BY r.calculated_at DESC, r.id DESC) AS rn
                FROM indicator_result r
                JOIN indicator_definition d ON d.code = r.indicator_code
               WHERE r.cod_imovel = :cod AND (:versao IS NULL OR r.versao_imovel_id = :versao)
            ) WHERE rn = 1 ORDER BY indicator_code
            """;

    /** Os campos do imóvel numa versão, como foram gravados. */
    public record Retrato(Map<String, Object> campos, String hash, BigDecimal areaGeometriaHa) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nomeado;

    public VersaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.nomeado = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<VersaoFonteResponse> versoesDaFonte(long fonteId) {
        List<VersaoFonteResponse> versoes = jdbc.query("""
                SELECT dv.id, dv.conjunto, dv.versao, dv.registros,
                       COALESCE(dv.publicada_em, e.finalizada_em, e.iniciada_em) AS quando,
                       e.id AS execucao_id, a.id AS arquivo_id, a.nome_original, a.hash_sha256, a.origem,
                       CASE WHEN dv.versao = MAX(dv.versao) OVER (PARTITION BY dv.conjunto) THEN 1 ELSE 0 END AS atual
                  FROM dataset_versao dv
                  JOIN execucao e      ON e.id = dv.execucao_id
                  JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
                 WHERE a.fonte_id = ?
                 ORDER BY dv.conjunto, dv.versao DESC
                """, (rs, n) -> new VersaoFonteResponse(
                rs.getLong("id"),
                rs.getString("conjunto"),
                rs.getInt("versao"),
                rs.getInt("atual") == 1,
                rs.getObject("quando", OffsetDateTime.class),
                rs.getObject("execucao_id") == null ? null : rs.getLong("execucao_id"),
                rs.getObject("arquivo_id") == null ? null : rs.getLong("arquivo_id"),
                rs.getString("nome_original"),
                rs.getString("hash_sha256"),
                rs.getString("origem"),
                rs.getObject("registros") == null ? null : rs.getInt("registros"),
                null, null, null), fonteId);

        if (versoes.stream().noneMatch(v -> CONJUNTO_IMOVEIS.equals(v.conjunto()))) {
            return versoes;
        }
        Map<Long, int[]> contagens = contagensDoHistorico();
        return versoes.stream().map(v -> {
            if (!CONJUNTO_IMOVEIS.equals(v.conjunto())) {
                return v;
            }
            int[] c = contagens.getOrDefault(v.id(), new int[3]);
            return new VersaoFonteResponse(v.id(), v.conjunto(), v.versao(), v.atual(), v.processadoEm(),
                    v.execucaoId(), v.arquivoId(), v.arquivo(), v.hash(), v.origem(), v.registros(),
                    c[0], c[1], c[2]);
        }).toList();
    }

    /**
     * Por versão do conjunto de imóveis: [novos, alterados, removidos]. Novo é o imóvel cuja
     * primeira versão (no histórico ou na linha atual) é aquela.
     */
    private Map<Long, int[]> contagensDoHistorico() {
        Map<Long, int[]> contagens = new HashMap<>();
        jdbc.query("""
                SELECT v, COUNT(*) FROM (
                  SELECT cod_imovel, MIN(dataset_versao_id) AS v FROM (
                    SELECT cod_imovel, dataset_versao_id FROM imovel_rural
                    UNION ALL
                    SELECT cod_imovel, dataset_versao_id FROM imovel_rural_historico)
                   GROUP BY cod_imovel)
                 GROUP BY v
                """, rs -> {
            contagens.computeIfAbsent(rs.getLong(1), k -> new int[3])[0] = rs.getInt(2);
        });
        jdbc.query("""
                SELECT substituida_por_versao_id, operacao, COUNT(*)
                  FROM imovel_rural_historico
                 WHERE substituida_por_versao_id IS NOT NULL
                 GROUP BY substituida_por_versao_id, operacao
                """, rs -> {
            int[] c = contagens.computeIfAbsent(rs.getLong(1), k -> new int[3]);
            c["ALTERADO".equals(rs.getString(2)) ? 1 : 2] = rs.getInt(3);
        });
        return contagens;
    }

    public boolean imovelExiste(String codImovel) {
        Integer achados = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM imovel_rural WHERE cod_imovel = ?)
                     + (SELECT COUNT(*) FROM imovel_rural_historico WHERE cod_imovel = ?) FROM dual
                """, Integer.class, codImovel, codImovel);
        return achados != null && achados > 0;
    }

    /** Todas as versões do imóvel, da mais nova para a mais antiga. */
    public List<VersaoImovelResponse> versoesDoImovel(String codImovel) {
        return jdbc.query(SQL_VERSOES_IMOVEL, (rs, n) -> versaoImovel(rs), codImovel, codImovel);
    }

    public Optional<Retrato> retrato(String codImovel, long datasetVersaoId) {
        return jdbc.query(SQL_RETRATO, (rs, n) -> {
            Map<String, Object> campos = new LinkedHashMap<>();
            campos.put("municipio", rs.getString("municipio"));
            campos.put("estado", rs.getString("estado"));
            campos.put("area_ha", rs.getBigDecimal("area_ha"));
            campos.put("situacao", rs.getString("situacao"));
            campos.put("municipio_id", rs.getObject("municipio_id") == null ? null : rs.getLong("municipio_id"));
            return new Retrato(campos, rs.getString("hash_registro"), rs.getBigDecimal("area_geometria"));
        }, codImovel, datasetVersaoId, codImovel, datasetVersaoId).stream().findFirst();
    }

    public boolean geometriaIgual(String codImovel, long de, long para) {
        List<String> relacao = jdbc.queryForList(SQL_GEOMETRIA_IGUAL, String.class, codImovel, codImovel, de, para);
        return !relacao.isEmpty() && "EQUAL".equals(relacao.get(0));
    }

    /** O resultado mais recente de cada indicador cadastrado; com versão, só os daquela versão do imóvel. */
    public List<IndicadorCadastradoResultado> indicadoresCadastrados(String codImovel, Long versaoImovelId) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("cod", codImovel)
                .addValue("versao", versaoImovelId);
        return nomeado.query(SQL_INDICADORES_CADASTRADOS, parametros, (rs, n) -> new IndicadorCadastradoResultado(
                rs.getString("indicator_code"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getBigDecimal("value"),
                rs.getString("status"),
                rs.getObject("dataset_versao_id") == null ? null : rs.getLong("dataset_versao_id"),
                rs.getObject("versao_imovel_id") == null ? null : rs.getLong("versao_imovel_id"),
                rs.getObject("calculated_at", OffsetDateTime.class)));
    }

    private static VersaoImovelResponse versaoImovel(ResultSet rs) throws SQLException {
        return new VersaoImovelResponse(
                rs.getLong("id"),
                rs.getString("conjunto"),
                rs.getInt("versao"),
                rs.getString("situacao"),
                rs.getObject("quando", OffsetDateTime.class),
                rs.getObject("execucao_id") == null ? null : rs.getLong("execucao_id"),
                rs.getObject("arquivo_id") == null ? null : rs.getLong("arquivo_id"),
                rs.getString("nome_original"),
                rs.getString("hash_sha256"),
                rs.getString("sigla"),
                rs.getString("nome"));
    }
}
