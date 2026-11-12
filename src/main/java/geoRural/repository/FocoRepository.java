package geoRural.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import geoRural.acesso.AcessoTerritorial;

/** Focos de calor para o mapa, em SQL direto (mesmo filtro por caixa da consulta de imóveis). */
@Repository
public class FocoRepository {

    public record FocoNoMapa(String idInpe, LocalDateTime dataHora, String satelite, String municipio,
                             BigDecimal frp, double lon, double lat) {}

    private final JdbcTemplate jdbc;
    private final AcessoTerritorial acesso;

    public FocoRepository(JdbcTemplate jdbc, AcessoTerritorial acesso) {
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    public List<FocoNoMapa> buscarNaArea(double minLon, double minLat, double maxLon, double maxLat,
                                         String satelite, LocalDateTime inicio, LocalDateTime fim, int limite) {
        List<Object> parametros = new ArrayList<>(List.of(minLon, minLat, maxLon, maxLat, satelite,
                Timestamp.valueOf(inicio), Timestamp.valueOf(fim)));
        List<String> regiao = new ArrayList<>();
        acesso.filtrarMunicipios("f.cod_ibge", regiao, parametros);
        parametros.add(limite);

        StringBuilder sql = new StringBuilder("""
                SELECT f.id_inpe, f.data_hora, f.satelite, f.municipio, f.frp,
                       f.geometria.sdo_point.x AS lon, f.geometria.sdo_point.y AS lat
                  FROM foco_calor f
                 WHERE SDO_FILTER(f.geometria,
                                  SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 3),
                                               SDO_ORDINATE_ARRAY(?, ?, ?, ?))) = 'TRUE'
                   AND f.satelite = ?
                   AND f.data_hora >= ?
                   AND f.data_hora < ?
                """);
        regiao.forEach(condicao -> sql.append(" AND ").append(condicao));
        sql.append(" AND ROWNUM <= ?");

        return jdbc.query(sql.toString(),
                (rs, n) -> new FocoNoMapa(
                        rs.getString("id_inpe"),
                        rs.getTimestamp("data_hora").toLocalDateTime(),
                        rs.getString("satelite"),
                        rs.getString("municipio"),
                        rs.getBigDecimal("frp"),
                        rs.getDouble("lon"),
                        rs.getDouble("lat")),
                parametros.toArray());
    }
}
