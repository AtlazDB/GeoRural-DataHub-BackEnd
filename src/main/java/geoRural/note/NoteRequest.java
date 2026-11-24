package geoRural.note;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Texto novo da anotação. Vazio vale: é como a pessoa apaga tudo. */
public record NoteRequest(
        @NotNull(message = "Envie o texto da anotação (pode ser vazio).")
        @Size(max = 20000, message = "A anotação pode ter no máximo 20.000 caracteres.")
        String content) {
}
