package geoRural.usuario.controller;

import geoRural.usuario.dto.DescricaoPerfilRequest;
import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.NovoPerfilRequest;
import geoRural.usuario.dto.NovoUsuarioRequest;
import geoRural.usuario.dto.PerfilResponse;
import geoRural.usuario.dto.PerfisRequest;
import geoRural.usuario.dto.SenhaRequest;
import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @Operation(summary = "Cria um usuário com perfis e cidades",
            description = "A senha é provisória: o Keycloak pede uma nova no primeiro login. "
                    + "Login ou e-mail repetido responde 409.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody NovoUsuarioRequest corpo, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(corpo, jwt.getClaimAsString("preferred_username"));
    }

    @Operation(summary = "Perfis que podem ser atribuídos: os 4 do sistema e os combinados")
    @GetMapping("/perfis")
    public List<PerfilResponse> perfis() {
        return service.listarPerfis();
    }

    @Operation(summary = "Cria um perfil combinado (junta perfis do sistema)",
            description = "Vira uma role composta no Keycloak. Nome repetido responde 409.")
    @PostMapping("/perfis")
    @ResponseStatus(HttpStatus.CREATED)
    public PerfilResponse criarPerfil(@Valid @RequestBody NovoPerfilRequest corpo) {
        return service.criarPerfil(corpo);
    }

    @Operation(summary = "Muda a descrição de um perfil combinado",
            description = "Os 4 perfis do sistema não podem ser editados (409).")
    @PutMapping("/perfis/{nome}")
    public PerfilResponse editarPerfil(@PathVariable String nome, @Valid @RequestBody DescricaoPerfilRequest corpo) {
        return service.editarDescricaoPerfil(nome, corpo.descricao());
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

    @Operation(summary = "Redefine a senha do usuário (perda ou esquecimento)",
            description = "A senha é provisória: o Keycloak pede uma nova no próximo login. 204 · 400 · 404.")
    @PostMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable String id, @Valid @RequestBody SenhaRequest corpo) {
        service.redefinirSenha(id, corpo.senhaProvisoria());
    }

    @Operation(summary = "Ativa o usuário (volta a conseguir entrar)")
    @PostMapping("/{id}/ativar")
    public UsuarioResponse ativar(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return service.alterarAtivo(id, true, jwt.getSubject());
    }

    @Operation(summary = "Desativa o usuário (não consegue entrar; perfis e cidades ficam guardados)",
            description = "O gestor não pode desativar a si mesmo.")
    @PostMapping("/{id}/desativar")
    public UsuarioResponse desativar(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return service.alterarAtivo(id, false, jwt.getSubject());
    }

    @Operation(summary = "Cidades que o usuário pode consultar",
            description = "Só restringe o perfil ANALISTA. todas = true quando não há nenhuma cidade na lista.")
    @GetMapping("/{id}/municipios")
    public MunicipiosResponse municipios(@PathVariable String id) {
        return service.municipios(id);
    }

    @Operation(summary = "Define as cidades que o usuário pode consultar (substitui a lista atual)",
            description = "todas = true, ou lista vazia, libera o Paraná inteiro.")
    @PutMapping("/{id}/municipios")
    public MunicipiosResponse definirMunicipios(
            @PathVariable String id,
            @RequestBody MunicipiosRequest corpo,
            @AuthenticationPrincipal Jwt jwt) {

        return service.definirMunicipios(id, corpo, jwt.getClaimAsString("preferred_username"));
    }
}