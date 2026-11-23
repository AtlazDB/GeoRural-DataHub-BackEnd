
package geoRural.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import geoRural.dto.IndicadorMunicipalResponse;

@Repository
public class IndicadorMunicipalRepository {

    private final JdbcTemplate jdbc;

    public IndicadorMunicipalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<IndicadorMunicipalResponse> listarIrlMunicipal(
            String estado,
            String codIbge) {

        StringBuilder sql = new StringBuilder("""
                WITH indicador_atual AS (
                    SELECT
                        ii.cod_imovel,
                        ii.indicador,
                        ii.status,
                        ii.numerador,
                        ii.area_imovel_ha,
                        ROW_NUMBER() OVER (
                            PARTITION BY ii.cod_imovel, ii.indicador
                            ORDER BY ii.calculado_em DESC, ii.id DESC
                        ) AS rn
                    FROM indicador_imovel ii
                    WHERE ii.indicador = 'IRL'
                ),
                base AS (
                    SELECT
                        m.cod_ibge,
                        m.nome AS municipio,
                        m.estado,
                        ia.cod_imovel,
                        ia.status,
                        ia.numerador,
                        ia.area_imovel_ha
                    FROM indicador_atual ia
                    JOIN imovel_rural i
                        ON i.cod_imovel = ia.cod_imovel
                    JOIN municipio m
                        ON m.id = i.municipio_id
                    WHERE ia.rn = 1
                      AND ia.area_imovel_ha > 0
                      AND ia.numerador IS NOT NULL
                )
                SELECT
                    cod_ibge,
                    municipio,
                    estado,
                    'IRL' AS indicador,
                    COUNT(*) AS total_imoveis,
                    SUM(numerador) AS area_reserva_legal_ha,
                    SUM(area_imovel_ha) AS area_total_imoveis_ha,
                    ROUND(
                        SUM(numerador) /
                        NULLIF(SUM(area_imovel_ha), 0) * 100,
                        4
                    ) AS valor_municipal,
                    SUM(CASE
                        WHEN status = 'CONFORME' THEN 1
                        ELSE 0
                    END) AS total_conformes,
                    SUM(CASE
                        WHEN status = 'CRITICO' THEN 1
                        ELSE 0
                    END) AS total_criticos
                FROM base
                WHERE 1 = 1
                """);

        List<Object> parametros = new ArrayList<>();

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND estado = ?");
            parametros.add(estado.toUpperCase());
        }

        if (codIbge != null && !codIbge.isBlank()) {
            sql.append(" AND cod_ibge = ?");
            parametros.add(codIbge);
        }

        sql.append("""
                GROUP BY cod_ibge, municipio, estado
                ORDER BY municipio
                """);

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new IndicadorMunicipalResponse(
                        rs.getString("cod_ibge"),
                        rs.getString("municipio"),
                        rs.getString("estado"),
                        rs.getString("indicador"),
                        rs.getLong("total_imoveis"),
                        rs.getBigDecimal("area_reserva_legal_ha"),
                        rs.getBigDecimal("area_total_imoveis_ha"),
                        rs.getBigDecimal("valor_municipal"),
                        rs.getLong("total_conformes"),
                        rs.getLong("total_criticos")
                ),
                parametros.toArray()
        );
            }
}
