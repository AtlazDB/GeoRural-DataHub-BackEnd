package geoRural.indicator.repository;

import geoRural.indicator.dto.IndicatorCalculationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Os disparos do cálculo de indicador (indicator_calculation, V29). O back cria a linha NA_FILA;
 * daí em diante quem grava é a DAG calcular_indicador (andamento, CONCLUIDO ou ERRO).
 */
@Repository
public class IndicatorCalculationRepository {

    private static final String COLUMNS = """
            id, indicator_code, status, progress, progress_step,
            requested_at, started_at, finished_at, error_message""";
    private static final String ACTIVE = "status IN ('NA_FILA', 'EM_ANDAMENTO')";

    private final JdbcTemplate jdbc;

    public IndicatorCalculationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria o disparo NA_FILA e devolve o id (vai para a DAG como calculation_id). */
    public long create(String code) {
        KeyHolder chave = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO indicator_calculation (indicator_code) VALUES (?)", new String[]{"ID"});
            ps.setString(1, code);
            return ps;
        }, chave);
        return chave.getKey().longValue();
    }

    public void setDagRunId(long id, String dagRunId) {
        jdbc.update("UPDATE indicator_calculation SET dag_run_id = ? WHERE id = ?", dagRunId, id);
    }

    /** O disparo nem chegou ao Airflow: fecha a linha com o erro, para não ficar NA_FILA para sempre. */
    public void markError(long id, String message) {
        jdbc.update("UPDATE indicator_calculation SET status = 'ERRO', finished_at = SYSTIMESTAMP, error_message = ?"
                + " WHERE id = ? AND " + ACTIVE, truncate(message), id);
    }

    public Optional<IndicatorCalculationResponse> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM indicator_calculation WHERE id = ?", this::toResponse, id)
                .stream().findFirst();
    }

    /** O cálculo na fila ou rodando deste indicador, se houver. */
    public Optional<IndicatorCalculationResponse> findActive(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM indicator_calculation WHERE indicator_code = ? AND " + ACTIVE
                        + " ORDER BY requested_at DESC, id DESC FETCH FIRST 1 ROWS ONLY", this::toResponse, code)
                .stream().findFirst();
    }

    /** Um cálculo ainda ativo e a execução dele no Airflow. */
    public record ActiveRun(long id, String dagRunId) {
    }

    /** Os cálculos na fila ou rodando que já têm execução no Airflow (para conferir se ela ainda existe). */
    public List<ActiveRun> findActiveRuns() {
        return jdbc.query("SELECT id, dag_run_id FROM indicator_calculation WHERE dag_run_id IS NOT NULL AND " + ACTIVE,
                (rs, rowNum) -> new ActiveRun(rs.getLong("id"), rs.getString("dag_run_id")));
    }

    /** O último disparo de cada indicador: é o que a tela mostra (barra, concluído ou erro). */
    public List<IndicatorCalculationResponse> findLatestOfEach() {
        return jdbc.query("""
                SELECT %s
                  FROM (SELECT c.*, ROW_NUMBER() OVER (PARTITION BY indicator_code
                                                       ORDER BY requested_at DESC, id DESC) AS rn
                          FROM indicator_calculation c)
                 WHERE rn = 1
                 ORDER BY indicator_code
                """.formatted(COLUMNS), this::toResponse);
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 4000 ? message : message.substring(0, 4000);
    }

    private IndicatorCalculationResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        return new IndicatorCalculationResponse(
                rs.getLong("id"),
                rs.getString("indicator_code"),
                rs.getString("status"),
                rs.getInt("progress"),
                rs.getString("progress_step"),
                rs.getObject("requested_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getString("error_message"));
    }
}
