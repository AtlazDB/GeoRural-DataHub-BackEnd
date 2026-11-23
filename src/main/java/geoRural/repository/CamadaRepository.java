package geoRural.repository;

import geoRural.acesso.AcessoTerritorial;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Camadas extras do mapa: a Reserva Legal e cada fonte do insumo genérico (APP, vegetação nativa,
 * UC...). Mesma ideia do mapa de imóveis: SDO_FILTER (só o índice espacial, pela caixa envolvente)
 * na caixa da tela, com limite de linhas, e a geometria em WKB para o service virar GeoJSON.
 */
@Repository
public class CamadaRepository {

    /** O SDO_FILTER com a caixa da tela; os quatro valores vão como parâmetro. */
    private static final String NA_CAIXA = """
            SDO_FILTER(geometria,
                       SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 3),
                                    SDO_ORDINATE_ARRAY(?, ?, ?, ?))) = 'TRUE'""";

    private final JdbcTemplate jdbc;
    private final AcessoTerritorial acesso;

    public CamadaRepository(JdbcTemplate jdbc, AcessoTerritorial acesso) {
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    /** Uma fonte do insumo genérico que já tem carga (dataset_versao com o conjunto dela). */
    public record FonteInsumo(Long id, String sigla, String nome) {}

    public record ReservaNoMapa(String codImovel, BigDecimal areaHa, String situacao, byte[] wkb) {}

    public record InsumoNoMapa(String categoria, BigDecimal valor, LocalDateTime dataReferencia, byte[] wkb) {}

    public boolean temReservaLegal() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM car_reserva_legal WHERE ROWNUM = 1", (rs, n) -> rs.getInt(1) > 0));
    }

    /**
     * Fontes ativas com carga no insumo genérico. O conjunto da carga é "insumo_" + sigla em
     * minúsculas (gravacao.conjunto_da_carga, no Airflow).
     */
    public List<FonteInsumo> fontesDoInsumo() {
        return jdbc.query("""
                SELECT f.id, f.sigla, f.nome
                  FROM fonte f
                 WHERE f.ativa = 1
                   AND EXISTS (SELECT 1 FROM dataset_versao dv WHERE dv.conjunto = 'insumo_' || LOWER(f.sigla))
                 ORDER BY f.nome""",
                (rs, n) -> new FonteInsumo(rs.getLong("id"), rs.getString("sigla"), rs.getString("nome")));
    }

    public Optional<FonteInsumo> fonteDoInsumo(long id) {
        return fontesDoInsumo().stream().filter(f -> f.id() == id).findFirst();
    }

    /**
     * Reserva Legal na caixa, no máximo {@code limite} linhas. Tem cod_imovel, então o analista
     * restrito a uma região só vê a dos imóveis dela (mesma regra do mapa de imóveis).
     */
    public List<ReservaNoMapa> reservaLegalNaArea(double minLon, double minLat, double maxLon, double maxLat,
                                                  int limite) {
        List<Object> parametros = new ArrayList<>(List.of(minLon, minLat, maxLon, maxLat));
        List<String> condicoes = new ArrayList<>(List.of(NA_CAIXA));
        acesso.filtrarImoveis("cod_imovel", condicoes, parametros);
        condicoes.add("ROWNUM <= ?");
        parametros.add(limite);

        String sql = "SELECT cod_imovel, area_ha, situacao, SDO_UTIL.TO_WKBGEOMETRY(geometria) AS wkb"
                + " FROM car_reserva_legal WHERE " + String.join(" AND ", condicoes);
        return jdbc.query(sql, (rs, n) -> new ReservaNoMapa(
                rs.getString("cod_imovel"),
                rs.getBigDecimal("area_ha"),
                rs.getString("situacao"),
                rs.getBytes("wkb")
        ), parametros.toArray());
    }

    /**
     * Feições de uma fonte do insumo genérico na caixa. Lê o WKB gravado na carga (V30) e só
     * converte na hora a linha antiga que ainda não tem.
     */
    public List<InsumoNoMapa> insumoNaArea(String sigla, double minLon, double minLat, double maxLon, double maxLat,
                                           int limite) {
        String sql = "SELECT categoria, valor, data_referencia,"
                + " COALESCE(geometria_wkb, SDO_UTIL.TO_WKBGEOMETRY(geometria)) AS wkb"
                + " FROM input_feature WHERE " + NA_CAIXA
                + " AND dataset_versao_id IN (SELECT id FROM dataset_versao WHERE conjunto = 'insumo_' || LOWER(?))"
                + " AND ROWNUM <= ?";
        return jdbc.query(sql, (rs, n) -> {
            Timestamp data = rs.getTimestamp("data_referencia");
            return new InsumoNoMapa(
                    rs.getString("categoria"),
                    rs.getBigDecimal("valor"),
                    data == null ? null : data.toLocalDateTime(),
                    rs.getBytes("wkb"));
        }, minLon, minLat, maxLon, maxLat, sigla, limite);
    }
}
