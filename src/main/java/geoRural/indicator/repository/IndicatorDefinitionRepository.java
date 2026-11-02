package geoRural.indicator.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Indicadores cadastrados (indicator_definition) em SQL direto. {@code parameters} e
// {@code ranges} são gravados como JSON, que a DAG de cálculo lê.


@Repository
public class IndicatorDefinitionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private static final String SELECT = """
            SELECT code, name, unit, description, fonte_id, calculation_model,
                   parameters, ranges, active, created_at
              FROM indicator_definition
            """;

    public IndicatorDefinitionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public boolean existsByCode(String code) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM indicator_definition WHERE code = ?", Integer.class, code);
        return total != null && total > 0;
    }

    public void insert(IndicatorDefinitionRequest request) {
        jdbc.update("""
                INSERT INTO indicator_definition
                       (code, name, unit, description, fonte_id, calculation_model, parameters, ranges)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.code(),
                request.name(),
                request.unit(),
                request.description(),
                request.fonteId(),
                request.calculationModel(),
                request.parameters() == null ? "{}" : toJson(request.parameters()),
                toJson(request.ranges()));
    }

    public List<IndicatorDefinitionResponse> findAll() {
        return jdbc.query(SELECT + " ORDER BY code", this::toResponse);
    }

    public Optional<IndicatorDefinitionResponse> findByCode(String code) {
        return jdbc.query(SELECT + " WHERE code = ?", this::toResponse, code)
                .stream().findFirst();
    }

    // Devolve false se a sigla não existe. Não apaga: os resultados continuam apontando para ela.
    public boolean deactivate(String code) {
        return jdbc.update("UPDATE indicator_definition SET active = 0 WHERE code = ?", code) > 0;
    }


     // Tipos de geometria que a fonte tem na input_feature (vazio = ainda sem dados).
     // 1 = ponto, 2 = linha, 3 = polígono, 5 = multiponto, 6 = multilinha, 7 = multipolígono.
     // A fonte de cada linha vem pela versão: dataset_versao -> execucao -> arquivo_bruto.

    public List<Integer> findGeometryTypesBySource(Long fonteId) {
        return jdbc.queryForList("""
                SELECT DISTINCT f.geometria.get_gtype()
                  FROM input_feature f
                  JOIN dataset_versao dv ON dv.id = f.dataset_versao_id
                  JOIN execucao e        ON e.id  = dv.execucao_id
                  JOIN arquivo_bruto a   ON a.id  = e.arquivo_bruto_id
                 WHERE a.fonte_id = ?
                """, Integer.class, fonteId);
    }

    private IndicatorDefinitionResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        return new IndicatorDefinitionResponse(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getString("description"),
                rs.getLong("fonte_id"),
                rs.getString("calculation_model"),
                fromJson(rs.getString("parameters"), new TypeReference<Map<String, Object>>() {}),
                fromJson(rs.getString("ranges"), new TypeReference<List<StatusRangeRequest>>() {}),
                rs.getInt("active") == 1,
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            // A coluna tem CHECK (... IS JSON): só chega aqui JSON válido
            throw new IllegalStateException("JSON inválido na indicator_definition", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Não acontece com o que veio do próprio JSON da requisição
            throw new IllegalStateException("Não foi possível converter para JSON", e);
        }
    }
}

