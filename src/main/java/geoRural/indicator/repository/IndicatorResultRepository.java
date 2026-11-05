package geoRural.indicator.repository;

import geoRural.indicator.dto.IndicatorResultResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;


@Repository
public class IndicatorResultRepository {

    private final JdbcTemplate jdbc;

    public IndicatorResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<IndicatorResultResponse> findLatest(String code, String codImovel) {
        return jdbc.query("""
                SELECT r.indicator_code, r.cod_imovel, r.value, r.status, r.numerator,
                       r.area_imovel_ha, r.calculated_at
                  FROM indicator_result r
                  JOIN imovel_rural i
                    ON i.cod_imovel        = r.cod_imovel
                   AND i.dataset_versao_id = r.versao_imovel_id
                 WHERE r.indicator_code = ?
                   AND r.cod_imovel     = ?
                 ORDER BY r.calculated_at DESC, r.id DESC
                 FETCH FIRST 1 ROWS ONLY
                """, this::toResponse, code, codImovel)
                .stream().findFirst();
    }

    private IndicatorResultResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        return new IndicatorResultResponse(
                rs.getString("indicator_code"),
                rs.getString("cod_imovel"),
                rs.getBigDecimal("value"),
                rs.getString("status"),
                rs.getBigDecimal("numerator"),
                rs.getBigDecimal("area_imovel_ha"),
                rs.getObject("calculated_at", OffsetDateTime.class));
    }
}