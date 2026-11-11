package geoRural.usuario.service;

import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.exception.OperacaoNaoPermitidaException;
import geoRural.usuario.exception.PerfilInvalidoException;
import geoRural.usuario.keycloak.KcPerfil;
import geoRural.usuario.keycloak.KcUsuario;
import geoRural.usuario.keycloak.KeycloakAdminClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    /** Os únicos perfis que esta tela pode atribuir, na ordem de exibição. */
    static final List<String> PERFIS_DO_SISTEMA = List.of("OPERADOR_DADOS", "ANALISTA", "AUDITOR", "GESTOR");

    private static final int TAMANHO_MAXIMO = 50;

    private final KeycloakAdminClient keycloak;

    public UsuarioServiceImpl(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public List<UsuarioResponse> listar(String busca, int pagina, int tamanho) {
        int tam = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        int pag = Math.max(pagina, 0);
        return keycloak.listarUsuarios(busca, pag * tam, tam).stream()
                .map(this::montar)
                .toList();
    }

    @Override
    public UsuarioResponse buscar(String id) {
        return montar(keycloak.buscarUsuario(id));
    }

    @Override
    public UsuarioResponse definirPerfis(String id, Set<String> perfis, String idLogado) {
        Set<String> novos = validar(perfis);

        if (id.equals(idLogado) && !novos.contains("GESTOR")) {
            throw new OperacaoNaoPermitidaException("Você não pode remover o seu próprio perfil de Gestor.");
        }

        KcUsuario usuario = keycloak.buscarUsuario(id);   // 404 antes de mexer em qualquer coisa

        List<KcPerfil> atuais = keycloak.perfisDoUsuario(id).stream()
                .filter(p -> PERFIS_DO_SISTEMA.contains(p.name()))
                .toList();
        Set<String> nomesAtuais = atuais.stream().map(KcPerfil::name).collect(Collectors.toSet());

        // Primeiro atribui o que falta; só depois remove o que sobra.
        for (String nome : novos) {
            if (!nomesAtuais.contains(nome)) {
                keycloak.atribuirPerfil(id, keycloak.buscarPerfil(nome));
            }
        }
        for (KcPerfil atual : atuais) {
            if (!novos.contains(atual.name())) {
                keycloak.removerPerfil(id, atual);
            }
        }

        return montar(usuario);
    }

    // ------------------------------------------------------------------- apoio

    private Set<String> validar(Set<String> pedidos) {
        Set<String> limpos = new HashSet<>();
        if (pedidos != null) {
            for (String p : pedidos) {
                String nome = p == null ? "" : p.trim();
                if (!PERFIS_DO_SISTEMA.contains(nome)) {
                    throw new PerfilInvalidoException(nome);
                }
                limpos.add(nome);
            }
        }
        return limpos;
    }

    private UsuarioResponse montar(KcUsuario u) {
        List<String> perfis = keycloak.perfisDoUsuario(u.id()).stream()
                .map(KcPerfil::name)
                .filter(PERFIS_DO_SISTEMA::contains)
                .sorted(Comparator.comparingInt(PERFIS_DO_SISTEMA::indexOf))
                .toList();

        String nome = ((u.firstName() == null ? "" : u.firstName()) + " "
                + (u.lastName() == null ? "" : u.lastName())).trim();
        if (nome.isEmpty()) {
            nome = u.username();
        }

        return new UsuarioResponse(u.id(), u.username(), nome, u.email(),
                Boolean.TRUE.equals(u.enabled()), perfis);
    }
}