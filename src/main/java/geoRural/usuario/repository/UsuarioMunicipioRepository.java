package geoRural.usuario.repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import geoRural.usuario.dto.MunicipioLiberado;

/** Municípios liberados para cada usuário (tabela usuario_municipio, V26). */
@Repository
public class UsuarioMunicipioRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nomeado;

    public UsuarioMunicipioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.nomeado = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<MunicipioLiberado> listar(String usuarioId) {
        return jdbc.query("""
                SELECT um.cod_ibge, m.nome
                  FROM usuario_municipio um
                  JOIN municipio m ON m.cod_ibge = um.cod_ibge
                 WHERE um.usuario_id = ?
                 ORDER BY m.nome
                """, (rs, n) -> new MunicipioLiberado(rs.getString("cod_ibge"), rs.getString("nome")), usuarioId);
    }

    public Set<String> codigos(String usuarioId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT cod_ibge FROM usuario_municipio WHERE usuario_id = ?", String.class, usuarioId));
    }

    /** Quantas cidades cada usuário tem; quem não aparece no mapa tem acesso a todas. */
    public Map<String, Integer> contarPorUsuario(Collection<String> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        return nomeado.query("""
                SELECT usuario_id, COUNT(*) AS total FROM usuario_municipio
                 WHERE usuario_id IN (:ids) GROUP BY usuario_id
                """, new MapSqlParameterSource("ids", usuarioIds), (rs, n) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Os códigos da lista que não existem na tabela municipio. */
    public Set<String> inexistentes(Collection<String> codigos) {
        if (codigos.isEmpty()) {
            return Set.of();
        }
        Set<String> existentes = new HashSet<>(nomeado.queryForList(
                "SELECT cod_ibge FROM municipio WHERE cod_ibge IN (:cods)",
                new MapSqlParameterSource("cods", codigos), String.class));
        return codigos.stream().filter(c -> !existentes.contains(c)).collect(Collectors.toSet());
    }

    /** Troca a lista inteira. Lista vazia volta o usuário para "todas as cidades". */
    public void substituir(String usuarioId, Collection<String> codigos, String liberadoPor) {
        jdbc.update("DELETE FROM usuario_municipio WHERE usuario_id = ?", usuarioId);
        if (codigos.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO usuario_municipio (usuario_id, cod_ibge, liberado_por) VALUES (?, ?, ?)",
                codigos.stream().map(c -> new Object[] {usuarioId, c, liberadoPor}).toList());
    }
}
