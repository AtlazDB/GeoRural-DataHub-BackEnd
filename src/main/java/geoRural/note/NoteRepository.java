package geoRural.note;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Anotação de cada usuário (tabela user_note, V32). */
@Repository
public class NoteRepository {

    private final JdbcTemplate jdbc;

    public NoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NoteResponse> find(String userId) {
        return jdbc.query("SELECT content, updated_at FROM user_note WHERE user_id = ?",
                (rs, n) -> new NoteResponse(
                        rs.getString("content") == null ? "" : rs.getString("content"),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                userId).stream().findFirst();
    }

    /** Cria ou troca a anotação do usuário. */
    public void save(String userId, String content) {
        jdbc.update("""
                MERGE INTO user_note n
                USING (SELECT ? AS user_id FROM dual) s
                   ON (n.user_id = s.user_id)
                 WHEN MATCHED THEN UPDATE SET n.content = ?, n.updated_at = SYSTIMESTAMP
                 WHEN NOT MATCHED THEN INSERT (user_id, content) VALUES (s.user_id, ?)
                """, userId, content, content);
    }
}
