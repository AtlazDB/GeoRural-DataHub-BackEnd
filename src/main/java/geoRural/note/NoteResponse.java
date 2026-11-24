package geoRural.note;

import java.time.OffsetDateTime;

/** Anotação do usuário logado. updatedAt é null enquanto ele nunca salvou nada. */
public record NoteResponse(String content, OffsetDateTime updatedAt) {

    public static NoteResponse empty() {
        return new NoteResponse("", null);
    }
}
