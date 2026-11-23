package geoRural.indicator.repository;

import geoRural.indicator.dto.IndicatorMunicipalityResponse;
import geoRural.indicator.dto.IndicatorResultResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
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

    // Agregação por município (D4). Só leitura: os valores já foram calculados pela DAG. Usa o
    // mesmo resultado do findLatest (o mais recente de cada imóvel, sobre a versão publicada).
    public List<IndicatorMunicipalityResponse> findByMunicipality(String code) {
        return jdbc.query("""
                WITH ultimo AS (
                    SELECT r.value, r.status, r.numerator, i.municipio_id,
                           ROW_NUMBER() OVER (PARTITION BY r.cod_imovel
                                              ORDER BY r.calculated_at DESC, r.id DESC) AS rn
                      FROM indicator_result r
                      JOIN imovel_rural i
                        ON i.cod_imovel        = r.cod_imovel
                       AND i.dataset_versao_id = r.versao_imovel_id
                     WHERE r.indicator_code = ?
                )
                SELECT m.cod_ibge, m.nome, m.estado,
                       COUNT(*)                                              AS imoveis,
                       SUM(CASE WHEN u.status = 'CONFORME'  THEN 1 ELSE 0 END) AS conforme,
                       SUM(CASE WHEN u.status = 'ALERTA'    THEN 1 ELSE 0 END) AS alerta,
                       SUM(CASE WHEN u.status = 'CRITICO'   THEN 1 ELSE 0 END) AS critico,
                       SUM(CASE WHEN u.status = 'VALIDACAO' THEN 1 ELSE 0 END) AS validacao,
                       ROUND(AVG(u.value), 4)                                AS media,
                       SUM(u.numerator)                                      AS soma_numerador
                  FROM ultimo u
                  LEFT JOIN municipio m ON m.id = u.municipio_id
                 WHERE u.rn = 1
                 GROUP BY m.cod_ibge, m.nome, m.estado
                 ORDER BY m.nome NULLS LAST
                """, (rs, rowNum) -> new IndicatorMunicipalityResponse(
                        rs.getString("cod_ibge"),
                        rs.getString("nome"),
                        rs.getString("estado"),
                        rs.getInt("imoveis"),
                        rs.getInt("conforme"),
                        rs.getInt("alerta"),
                        rs.getInt("critico"),
                        rs.getInt("validacao"),
                        rs.getBigDecimal("media"),
                        rs.getBigDecimal("soma_numerador")),
                code);
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