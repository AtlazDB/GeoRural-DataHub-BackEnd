package geoRural.usuario.controller;

import geoRural.usuario.dto.PerfisRequest;
import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Usuários", description = "Gestão de perfis de acesso (somente GESTOR)")
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @Operation(summary = "Lista usuários com seus perfis")
    @GetMapping
    public List<UsuarioResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {

        return service.listar(busca, pagina, tamanho);
    }

    @Operation(summary = "Um usuário com seus perfis")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable String id) {
        return service.buscar(id);
    }

    @Operation(summary = "Define os perfis do usuário (substitui o conjunto atual)")
    @PutMapping("/{id}/perfis")
    public UsuarioResponse definirPerfis(
            @PathVariable String id,
            @Valid @RequestBody PerfisRequest corpo,
            @AuthenticationPrincipal Jwt jwt) {

        return service.definirPerfis(id, corpo.perfis(), jwt.getSubject());
    }
}