package geoRural.usuario.keycloak;

import geoRural.config.KeycloakAdminProperties;
import geoRural.usuario.exception.KeycloakIndisponivelException;
import geoRural.usuario.exception.PerfilDuplicadoException;
import geoRural.usuario.exception.SenhaRecusadaException;
import geoRural.usuario.exception.UsuarioDuplicadoException;
import geoRural.usuario.exception.UsuarioNaoEncontradoException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import geoRural.usuario.exception.PerfilInvalidoException;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Conversa com a Admin API do Keycloak usando a conta técnica georural-backend (T26).
 * É o único lugar do back que sabe falar com o Keycloak.
 */
@Component
public class KeycloakAdminClient {

    private static final Duration FOLGA = Duration.ofSeconds(30);

    private final RestClient http;
    private final KeycloakAdminProperties props;

    private String token;
    private Instant expiraEm = Instant.EPOCH;

    public KeycloakAdminClient(@Qualifier("keycloakRestClient") RestClient http,
                               KeycloakAdminProperties props) {
        this.http = http;
        this.props = props;
    }

    // ---------------------------------------------------------------- leituras

    /** Lista usuários. busca (opcional) procura em nome, usuário e e-mail. */
    public List<KcUsuario> listarUsuarios(String busca, int primeiro, int maximo) {
        List<KcUsuario> lista = chamar(() -> http.get()
                .uri(b -> {
                    b.path("/admin/realms/{realm}/users")
                            .queryParam("first", primeiro)
                            .queryParam("max", maximo);
                    if (busca != null && !busca.isBlank()) {
                        b.queryParam("search", busca.trim());
                    }
                    return b.build(props.realm());
                })
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<KcUsuario>>() {}));
        return lista == null ? List.of() : lista;
    }

    /** Um usuário pelo id. Lança UsuarioNaoEncontradoException se não existir. */
    public KcUsuario buscarUsuario(String id) {
        try {
            return chamar(() -> http.get()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), id)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(KcUsuario.class));
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }

    /** Perfis (roles de realm) atribuídos diretamente ao usuário. */
    public List<KcPerfil> perfisDoUsuario(String id) {
        try {
            List<KcPerfil> lista = chamar(() -> http.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), id)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<KcPerfil>>() {}));
            return lista == null ? List.of() : lista;
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(id);
        }
    }

        // --------------------------------------------------------------- escritas

    /**
     * Cria o usuário, ativo, com a senha provisória (o Keycloak pede uma nova no primeiro login).
     * Devolve o id, que o Keycloak manda no cabeçalho Location. Login ou e-mail repetido: 409.
     */
    public String criarUsuario(String username, String email, String nome, String sobrenome, String senha) {
        Map<String, Object> corpo = Map.of(
                "username", username,
                "email", email,
                "firstName", nome,
                "lastName", sobrenome,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", senha, "temporary", true)));
        try {
            URI local = chamar(() -> http.post()
                    .uri("/admin/realms/{realm}/users", props.realm())
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation());
            String caminho = local.getPath();
            return caminho.substring(caminho.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw new UsuarioDuplicadoException();
        }
    }

    /** Busca o perfil pelo nome. Lança PerfilInvalidoException se o Keycloak não o conhece. */
    public KcPerfil buscarPerfil(String nome) {
        try {
            return chamar(() -> http.get()
                    .uri("/admin/realms/{realm}/roles/{nome}", props.realm(), nome)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(KcPerfil.class));
        } catch (HttpClientErrorException.NotFound e) {
            throw new PerfilInvalidoException(nome);
        }
    }

    /** Atribui o perfil ao usuário. */
    public void atribuirPerfil(String usuarioId, KcPerfil perfil) {
        try {
            chamar(() -> http.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), usuarioId)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(perfil))
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(usuarioId);
        }
    }

    /** Remove o perfil do usuário. */
    public void removerPerfil(String usuarioId, KcPerfil perfil) {
        try {
            chamar(() -> http.method(HttpMethod.DELETE)
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), usuarioId)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(perfil))
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(usuarioId);
        }
    }

    /** Liga ou desliga o login do usuário (campo enabled do Keycloak). Não mexe em perfis nem cidades. */
    public void alterarAtivo(String usuarioId, boolean ativo) {
        try {
            chamar(() -> http.put()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), usuarioId)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", ativo))
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(usuarioId);
        }
    }

    /**
     * Redefine a senha (perda ou esquecimento). Provisória: o Keycloak pede uma nova no próximo
     * login, como na criação. Senha fora das regras do realm: SenhaRecusadaException (400).
     */
    public void redefinirSenha(String usuarioId, String senha) {
        Map<String, Object> corpo = Map.of("type", "password", "value", senha, "temporary", true);
        try {
            chamar(() -> http.put()
                    .uri("/admin/realms/{realm}/users/{id}/reset-password", props.realm(), usuarioId)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound e) {
            throw new UsuarioNaoEncontradoException(usuarioId);
        } catch (KeycloakIndisponivelException e) {
            // O chamar() embrulha o 400; aqui ele quer dizer senha recusada, não Keycloak fora do ar.
            if (e.getCause() instanceof HttpClientErrorException.BadRequest) {
                throw new SenhaRecusadaException();
            }
            throw e;
        }
    }

    // ------------------------------------------------- perfis combinados (roles)

    /** Todas as roles de realm, com descrição e atributos (para achar os perfis criados pela tela). */
    public List<KcPerfilCompleto> listarPerfisDoRealm() {
        List<KcPerfilCompleto> lista = chamar(() -> http.get()
                .uri(u -> u.path("/admin/realms/{realm}/roles")
                        .queryParam("briefRepresentation", false)
                        .queryParam("first", 0)
                        .queryParam("max", 500)
                        .build(props.realm()))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<KcPerfilCompleto>>() {}));
        return lista == null ? List.of() : lista;
    }

    /** Os perfis que um perfil combinado inclui (composites). */
    public List<KcPerfil> compostosDoPerfil(String nome) {
        try {
            List<KcPerfil> lista = chamar(() -> http.get()
                    .uri("/admin/realms/{realm}/roles/{nome}/composites", props.realm(), nome)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<KcPerfil>>() {}));
            return lista == null ? List.of() : lista;
        } catch (HttpClientErrorException.NotFound e) {
            throw new PerfilInvalidoException(nome);
        }
    }

    /**
     * Cria a role, marcada com o atributo georural = perfil (é assim que a tela reconhece os perfis
     * que ela criou). Nome repetido: 409. Exige a permissão manage-realm na conta técnica.
     */
    public void criarPerfil(String nome, String descricao) {
        Map<String, Object> corpo = Map.of(
                "name", nome,
                "description", descricao == null ? "" : descricao,
                "attributes", Map.of(ATRIBUTO_PERFIL, List.of(VALOR_PERFIL)));
        try {
            chamar(() -> http.post()
                    .uri("/admin/realms/{realm}/roles", props.realm())
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.Conflict e) {
            throw new PerfilDuplicadoException(nome);
        }
    }

    /** Faz o perfil combinado incluir os perfis dados: quem recebe o combinado ganha todos eles. */
    public void adicionarCompostos(String nome, List<KcPerfil> incluidos) {
        chamar(() -> http.post()
                .uri("/admin/realms/{realm}/roles/{nome}/composites", props.realm(), nome)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(incluidos)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Troca a descrição da role. O Keycloak regrava nome, descrição e atributos juntos, então o
     * nome e a marca georural = perfil vão de novo, iguais.
     */
    public void atualizarDescricaoPerfil(String nome, String descricao) {
        Map<String, Object> corpo = Map.of(
                "name", nome,
                "description", descricao == null ? "" : descricao,
                "attributes", Map.of(ATRIBUTO_PERFIL, List.of(VALOR_PERFIL)));
        try {
            chamar(() -> http.put()
                    .uri("/admin/realms/{realm}/roles/{nome}", props.realm(), nome)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound e) {
            throw new PerfilInvalidoException(nome);
        }
    }

    /** Atributo que marca, no Keycloak, as roles criadas pela tela de perfis. */
    public static final String ATRIBUTO_PERFIL = "georural";
    public static final String VALOR_PERFIL = "perfil";

    // ------------------------------------------------------------------- apoio

    /**
     * Executa uma chamada ao Keycloak. 404 e 409 passam adiante (quem chamou decide o que significa);
     * qualquer outra falha vira KeycloakIndisponivelException.
     */
    private <T> T chamar(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict e) {
            throw e;
        } catch (HttpClientErrorException.Unauthorized e) {
            esquecerToken();
            throw new KeycloakIndisponivelException("O Keycloak recusou a credencial técnica do back.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            // Ex.: criar perfil sem a permissão manage-realm na conta técnica (realm-management).
            throw new KeycloakIndisponivelException(
                    "A conta técnica georural-backend não tem permissão para esta operação no Keycloak.", e);
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Não foi possível falar com o Keycloak.", e);
        }
    }

    private synchronized void esquecerToken() {
        token = null;
        expiraEm = Instant.EPOCH;
    }

    /** Token da conta técnica. Reaproveita o anterior até 30 s antes de vencer. */
    synchronized String token() {
        if (token != null && Instant.now().isBefore(expiraEm)) {
            return token;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        try {
            Map<String, Object> resposta = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            token = (String) resposta.get("access_token");
            long segundos = ((Number) resposta.get("expires_in")).longValue();
            expiraEm = Instant.now().plusSeconds(segundos).minus(FOLGA);
            return token;
        } catch (RestClientException | NullPointerException | ClassCastException e) {
            throw new KeycloakIndisponivelException("Não foi possível obter o token do Keycloak.", e);
        }
    }
}