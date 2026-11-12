
package geoRural.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import geoRural.repository.DashboardRepository.ExecucaoRecenteDados;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Total de fontes ativas
    public long contarFontesAtivas() {
        String sql = """
                SELECT COUNT(*)
                FROM FONTE
                WHERE ATIVA = 1
                """;

        return jdbc.queryForObject(sql, Long.class);
    }

    // Execuções iniciadas nos últimos 7 dias
    public long contarExecucoesUltimos7Dias() {
        String sql = """
                SELECT COUNT(*)
                FROM EXECUCAO
                WHERE INICIADA_EM >= SYSTIMESTAMP - INTERVAL '7' DAY
                """;

        return jdbc.queryForObject(sql, Long.class);
    }

    // Execuções com falha nos últimos 7 dias
    public long contarFalhasUltimos7Dias() {
        String sql = """
                SELECT COUNT(*)
                FROM EXECUCAO
                WHERE STATUS = 'FALHOU'
                  AND INICIADA_EM >= SYSTIMESTAMP - INTERVAL '7' DAY
                """;

        return jdbc.queryForObject(sql, Long.class);
    }

    // Registros aguardando análise na quarentena
    public long contarRegistrosQuarentena() {
        String sql = """
                SELECT COUNT(*)
                FROM REGISTRO_QUARENTENA
                WHERE STATUS_ANALISE = 'PENDENTE'
                """;

        return jdbc.queryForObject(sql, Long.class);
    }

    // Registros da versão mais recente de cada conjunto na zona tratada
    public long contarRegistrosZonaTratada() {
        String sql = """
                WITH versoes AS (
                    SELECT
                        ZONA,
                        REGISTROS,
                        ROW_NUMBER() OVER (
                            PARTITION BY CONJUNTO
                            ORDER BY VERSAO DESC, ID DESC
                        ) AS RN
                    FROM DATASET_VERSAO
                )
                SELECT NVL(SUM(NVL(REGISTROS, 0)), 0)
                FROM versoes
                WHERE RN = 1
                  AND ZONA = 'TRATADA'
                """;

        return jdbc.queryForObject(sql, Long.class);
    }

    // Últimas 5 execuções realizadas
    public List<ExecucaoRecenteDados> listarExecucoesRecentes() {

        String sql = """
                SELECT
                    f.NOME AS FONTE,
                    e.TIPO,
                    e.STATUS,
                    e.REGISTROS_LIDOS,
                    e.REGISTROS_VALIDOS,
                    e.REGISTROS_INVALIDOS,
                    e.INICIADA_EM
                FROM EXECUCAO e
                JOIN ARQUIVO_BRUTO a
                    ON a.ID = e.ARQUIVO_BRUTO_ID
                JOIN FONTE f
                    ON f.ID = a.FONTE_ID
                ORDER BY e.INICIADA_EM DESC
                FETCH FIRST 5 ROWS ONLY
                """;

        return jdbc.query(sql, (rs, rowNum) ->
        new ExecucaoRecenteDados(
                rs.getString("FONTE"),
                rs.getString("TIPO"),
                rs.getString("STATUS"),
                rs.getObject("REGISTROS_LIDOS") != null
                        ? ((Number) rs.getObject("REGISTROS_LIDOS")).intValue()
                        : null,
                rs.getObject("REGISTROS_VALIDOS") != null
                        ? ((Number) rs.getObject("REGISTROS_VALIDOS")).intValue()
                        : null,
                rs.getObject("REGISTROS_INVALIDOS") != null
                        ? ((Number) rs.getObject("REGISTROS_INVALIDOS")).intValue()
                        : null,
                rs.getObject("INICIADA_EM", OffsetDateTime.class)
        )
);

    }

    public record ExecucaoRecenteDados(
            String fonte,
            String tipo,
            String status,
            Integer registrosLidos,
            Integer registrosValidos,
            Integer registrosInvalidos,
            OffsetDateTime iniciadaEm
    ) {}
}
