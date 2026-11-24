package geoRural.note;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * "Minhas anotações" da tela inicial. Cada usuário só lê e grava a própria anotação:
 * o dono vem sempre do token (sub), nunca da URL ou do corpo.
 */
@RestController
@RequestMapping("/api/v1/notes/me")
@Tag(name = "Anotações", description = "Anotação livre do usuário logado na tela inicial (US13.T26)")
public class NoteController {

    private final NoteRepository notes;

    public NoteController(NoteRepository notes) {
        this.notes = notes;
    }

    @GetMapping
    @Operation(summary = "Anotação do usuário logado", description = "Texto vazio se ele nunca salvou nada.")
    public NoteResponse get(@AuthenticationPrincipal Jwt jwt) {
        return notes.find(jwt.getSubject()).orElseGet(NoteResponse::empty);
    }

    @PutMapping
    @Operation(summary = "Salva a anotação do usuário logado", description = "Troca o texto inteiro.")
    public NoteResponse save(@Valid @RequestBody NoteRequest body, @AuthenticationPrincipal Jwt jwt) {
        notes.save(jwt.getSubject(), body.content());
        return notes.find(jwt.getSubject()).orElseGet(NoteResponse::empty);
    }
}
