package geoRural.acesso;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import geoRural.exception.ImovelNaoEncontradoException;
import geoRural.usuario.repository.UsuarioMunicipioRepository;

/**
 * Região que o usuário logado pode consultar (US14). Só o perfil ANALISTA é restrito, e só
 * quando tem cidades na usuario_municipio; quem tem também outro perfil vê tudo. Fora de uma
 * requisição autenticada (jobs, testes) não há restrição.
 *
 * O município de um imóvel sai do próprio código do CAR: "PR-4102901-..." é de 4102901.
 */
@Component
public class AcessoTerritorial {

    private static final Set<String> PERFIS_SEM_RESTRICAO =
            Set.of("ROLE_GESTOR", "ROLE_OPERADOR_DADOS", "ROLE_AUDITOR");

    // Analista sem nenhuma cidade na tabela vê todas.
    private static final String SQL_CIDADE_LIBERADA = """
            (NOT EXISTS (SELECT 1 FROM usuario_municipio um WHERE um.usuario_id = ?)
             OR %s IN (SELECT um.cod_ibge FROM usuario_municipio um WHERE um.usuario_id = ?))""";

    private final UsuarioMunicipioRepository municipios;

    public AcessoTerritorial(UsuarioMunicipioRepository municipios) {
        this.municipios = municipios;
    }

    /** O id do usuário quando ele é só analista; vazio para os outros perfis. */
    public Optional<String> analista() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        Set<String> perfis = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (!perfis.contains("ROLE_ANALISTA") || perfis.stream().anyMatch(PERFIS_SEM_RESTRICAO::contains)) {
            return Optional.empty();
        }
        return Optional.of(jwt.getToken().getSubject());
    }

    /** Os códigos IBGE liberados; vazio quando o usuário pode ver o Paraná inteiro. */
    public Optional<Set<String>> municipiosLiberados() {
        return analista().map(municipios::codigos).filter(c -> !c.isEmpty());
    }

    public boolean permiteMunicipio(String codIbge) {
        return municipiosLiberados().map(c -> c.contains(codIbge)).orElse(true);
    }

    public boolean permiteImovel(String codImovel) {
        return municipiosLiberados().map(c -> c.contains(codIbgeDoImovel(codImovel))).orElse(true);
    }

    /** Imóvel fora da região responde como inexistente (404), para não confirmar que o código existe. */
    public void exigirImovel(String codImovel) {
        if (!permiteImovel(codImovel)) {
            throw new ImovelNaoEncontradoException(codImovel);
        }
    }

    /** Acrescenta a condição de região numa consulta de imóveis; colunaCodImovel é a coluna do cod_imovel. */
    public void filtrarImoveis(String colunaCodImovel, List<String> condicoes, List<Object> parametros) {
        filtrar("SUBSTR(" + colunaCodImovel + ", 4, 7)", condicoes, parametros);
    }

    /** Acrescenta a condição de região numa consulta com o código IBGE na coluna informada. */
    public void filtrarMunicipios(String colunaCodIbge, List<String> condicoes, List<Object> parametros) {
        filtrar(colunaCodIbge, condicoes, parametros);
    }

    private void filtrar(String expressao, List<String> condicoes, List<Object> parametros) {
        analista().ifPresent(id -> {
            condicoes.add(SQL_CIDADE_LIBERADA.formatted(expressao));
            parametros.add(id);
            parametros.add(id);
        });
    }

    static String codIbgeDoImovel(String codImovel) {
        if (codImovel == null || codImovel.length() < 10 || codImovel.charAt(2) != '-') {
            return null;
        }
        return codImovel.substring(3, 10);
    }
}
