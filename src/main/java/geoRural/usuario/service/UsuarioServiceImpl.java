package geoRural.usuario.service;

import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.NovoPerfilRequest;
import geoRural.usuario.dto.PerfilResponse;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.NovoUsuarioRequest;
import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.exception.MunicipioInvalidoException;
import geoRural.usuario.exception.OperacaoNaoPermitidaException;
import geoRural.usuario.exception.PerfilDuplicadoException;
import geoRural.usuario.exception.PerfilInvalidoException;
import geoRural.usuario.keycloak.KcPerfil;
import geoRural.usuario.keycloak.KcPerfilCompleto;
import geoRural.usuario.keycloak.KcUsuario;
import geoRural.usuario.keycloak.KeycloakAdminClient;
import geoRural.usuario.repository.UsuarioMunicipioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    /** Os 4 perfis do sistema (o que cada um pode fazer está no SecurityConfig), na ordem de exibição. */
    static final List<String> PERFIS_DO_SISTEMA = List.of("OPERADOR_DADOS", "ANALISTA", "AUDITOR", "GESTOR");

    private static final int TAMANHO_MAXIMO = 50;

    private final KeycloakAdminClient keycloak;
    private final UsuarioMunicipioRepository municipios;

    public UsuarioServiceImpl(KeycloakAdminClient keycloak, UsuarioMunicipioRepository municipios) {
        this.keycloak = keycloak;
        this.municipios = municipios;
    }

    @Override
    public List<UsuarioResponse> listar(String busca, int pagina, int tamanho) {
        int tam = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        int pag = Math.max(pagina, 0);
        List<KcUsuario> usuarios = keycloak.listarUsuarios(busca, pag * tam, tam);
        Map<String, Integer> cidades = municipios.contarPorUsuario(usuarios.stream().map(KcUsuario::id).toList());
        Map<String, List<String>> catalogo = catalogo();   // uma vez para a página toda
        return usuarios.stream()
                .map(u -> montar(u, cidades.getOrDefault(u.id(), 0), catalogo))
                .toList();
    }

    @Override
    public UsuarioResponse buscar(String id) {
        return montar(keycloak.buscarUsuario(id));
    }

    @Override
    @Transactional
    public UsuarioResponse criar(NovoUsuarioRequest pedido, String liberadoPor) {
        Set<String> perfis = limpar(pedido.perfis());
        validar(perfis, catalogoPara(perfis));
        Set<String> cidades = codigosValidos(pedido.todasCidades(), pedido.municipios());

        String id = keycloak.criarUsuario(pedido.username().trim(), pedido.email().trim(),
                pedido.nome().trim(), pedido.sobrenome().trim(), pedido.senhaProvisoria());
        for (String nome : perfis) {
            keycloak.atribuirPerfil(id, keycloak.buscarPerfil(nome));
        }
        municipios.substituir(id, cidades, liberadoPor);
        return buscar(id);
    }

    @Override
    public MunicipiosResponse municipios(String id) {
        keycloak.buscarUsuario(id);
        return MunicipiosResponse.de(municipios.listar(id));
    }

    @Override
    @Transactional
    public MunicipiosResponse definirMunicipios(String id, MunicipiosRequest pedido, String liberadoPor) {
        Set<String> codigos = codigosValidos(pedido.todas(), pedido.municipios());
        keycloak.buscarUsuario(id);   // 404 antes de gravar
        municipios.substituir(id, codigos, liberadoPor);
        return MunicipiosResponse.de(municipios.listar(id));
    }

    @Override
    public UsuarioResponse definirPerfis(String id, Set<String> perfis, String idLogado) {
        Set<String> novos = limpar(perfis);
        Map<String, List<String>> doPedido = catalogoPara(novos);
        validar(novos, doPedido);

        // Vale o Gestor direto ou dentro de um perfil combinado.
        if (id.equals(idLogado) && novos.stream().noneMatch(n -> doPedido.get(n).contains("GESTOR"))) {
            throw new OperacaoNaoPermitidaException("Você não pode remover o seu próprio perfil de Gestor.");
        }

        KcUsuario usuario = keycloak.buscarUsuario(id);   // 404 antes de mexer em qualquer coisa

        // Catálogo completo: o usuário pode ter hoje um perfil combinado que sai.
        Map<String, List<String>> catalogo = catalogo();
        List<KcPerfil> atuais = keycloak.perfisDoUsuario(id).stream()
                .filter(p -> catalogo.containsKey(p.name()))
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

        return montar(usuario, municipios.codigos(id).size(), catalogo);
    }

    @Override
    public UsuarioResponse alterarAtivo(String id, boolean ativo, String idLogado) {
        if (!ativo && id.equals(idLogado)) {
            throw new OperacaoNaoPermitidaException("Você não pode desativar o seu próprio usuário.");
        }
        keycloak.alterarAtivo(id, ativo);
        return buscar(id);
    }

    @Override
    public void redefinirSenha(String id, String senhaProvisoria) {
        keycloak.buscarUsuario(id);   // 404 antes de mexer na senha
        keycloak.redefinirSenha(id, senhaProvisoria);
    }

    // ------------------------------------------------- perfis combinados (US10)

    @Override
    public List<PerfilResponse> listarPerfis() {
        List<KcPerfilCompleto> criados = criadosPelaTela();
        // A descrição dos 4 do sistema fica no front, junto com o que cada um pode fazer.
        Map<String, String> descricoes = new HashMap<>();
        criados.forEach(r -> descricoes.put(r.name(), r.description()));
        return catalogo(criados).entrySet().stream()
                .map(e -> new PerfilResponse(e.getKey(), descricoes.get(e.getKey()),
                        PERFIS_DO_SISTEMA.contains(e.getKey()), e.getValue()))
                .toList();
    }

    @Override
    public PerfilResponse criarPerfil(NovoPerfilRequest pedido) {
        String nome = pedido.nome().trim().toUpperCase(Locale.ROOT);
        if (PERFIS_DO_SISTEMA.contains(nome)) {
            throw new PerfilDuplicadoException(nome);
        }
        // Só junta os 4 do sistema: um combinado dentro de outro ficaria difícil de entender.
        Set<String> inclui = limpar(pedido.inclui());
        for (String base : inclui) {
            if (!PERFIS_DO_SISTEMA.contains(base)) {
                throw new PerfilInvalidoException(base);
            }
        }
        List<String> ordenados = PERFIS_DO_SISTEMA.stream().filter(inclui::contains).toList();
        String descricao = pedido.descricao() == null ? "" : pedido.descricao().trim();

        keycloak.criarPerfil(nome, descricao);
        keycloak.adicionarCompostos(nome, ordenados.stream().map(keycloak::buscarPerfil).toList());
        return new PerfilResponse(nome, descricao, false, ordenados);
    }

    @Override
    public PerfilResponse editarDescricaoPerfil(String nome, String descricao) {
        String codigo = nome.trim().toUpperCase(Locale.ROOT);
        if (PERFIS_DO_SISTEMA.contains(codigo)) {
            throw new OperacaoNaoPermitidaException("Os 4 perfis do sistema não podem ser editados.");
        }
        // Só os combinados criados pela tela: roles de outras origens no Keycloak não são nossas.
        if (criadosPelaTela().stream().noneMatch(r -> r.name().equals(codigo))) {
            throw new PerfilInvalidoException(codigo);
        }
        String nova = descricao == null ? "" : descricao.trim();
        keycloak.atualizarDescricaoPerfil(codigo, nova);
        return new PerfilResponse(codigo, nova, false, catalogo().get(codigo));
    }

    /**
     * Os perfis que a tela pode atribuir, cada um com os perfis do sistema que ele dá: os 4 do
     * sistema (cada um dá a si mesmo) e os combinados criados pela tela (role marcada com o
     * atributo georural = perfil). Os combinados funcionam sem mexer na segurança: o Keycloak põe no
     * token os perfis que eles incluem.
     */
    private Map<String, List<String>> catalogo() {
        return catalogo(criadosPelaTela());
    }

    private Map<String, List<String>> catalogo(List<KcPerfilCompleto> criados) {
        Map<String, List<String>> catalogo = catalogoDoSistema();
        criados.stream()
                .map(KcPerfilCompleto::name)
                .sorted()
                .forEach(nome -> catalogo.put(nome, keycloak.compostosDoPerfil(nome).stream()
                        .map(KcPerfil::name)
                        .filter(PERFIS_DO_SISTEMA::contains)
                        .sorted(Comparator.comparingInt(PERFIS_DO_SISTEMA::indexOf))
                        .toList()));
        return catalogo;
    }

    /** Pedido só com os 4 do sistema não precisa perguntar ao Keycloak quais combinados existem. */
    private Map<String, List<String>> catalogoPara(Set<String> nomes) {
        return PERFIS_DO_SISTEMA.containsAll(nomes) ? catalogoDoSistema() : catalogo();
    }

    private static Map<String, List<String>> catalogoDoSistema() {
        Map<String, List<String>> catalogo = new LinkedHashMap<>();
        PERFIS_DO_SISTEMA.forEach(p -> catalogo.put(p, List.of(p)));
        return catalogo;
    }

    private List<KcPerfilCompleto> criadosPelaTela() {
        return keycloak.listarPerfisDoRealm().stream()
                .filter(UsuarioServiceImpl::criadoPelaTela)
                .toList();
    }

    private static boolean criadoPelaTela(KcPerfilCompleto role) {
        List<String> valores = role.attributes() == null ? null
                : role.attributes().get(KeycloakAdminClient.ATRIBUTO_PERFIL);
        return valores != null && valores.contains(KeycloakAdminClient.VALOR_PERFIL);
    }

    // ------------------------------------------------------------------- apoio

    /** Os códigos pedidos, sem espaços; vazio quando é para liberar todas as cidades. */
    private Set<String> codigosValidos(boolean todas, Set<String> pedidos) {
        Set<String> codigos = new HashSet<>();
        if (!todas && pedidos != null) {
            pedidos.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .map(String::trim)
                    .forEach(codigos::add);
        }
        Set<String> inexistentes = municipios.inexistentes(codigos);
        if (!inexistentes.isEmpty()) {
            throw new MunicipioInvalidoException(inexistentes);
        }
        return codigos;
    }

    /** Os nomes pedidos, sem espaços. */
    private static Set<String> limpar(Set<String> pedidos) {
        Set<String> limpos = new HashSet<>();
        if (pedidos != null) {
            for (String p : pedidos) {
                limpos.add(p == null ? "" : p.trim());
            }
        }
        return limpos;
    }

    /** Todo perfil pedido tem que estar no catálogo (os 4 do sistema ou um combinado da tela). */
    private static void validar(Set<String> nomes, Map<String, List<String>> catalogo) {
        for (String nome : nomes) {
            if (!catalogo.containsKey(nome)) {
                throw new PerfilInvalidoException(nome);
            }
        }
    }

    private UsuarioResponse montar(KcUsuario u) {
        return montar(u, municipios.codigos(u.id()).size(), catalogo());
    }

    // Mostra só os perfis do catálogo, na ordem dele: os 4 do sistema e depois os combinados.
    private UsuarioResponse montar(KcUsuario u, int totalMunicipios, Map<String, List<String>> catalogo) {
        List<String> ordem = List.copyOf(catalogo.keySet());
        List<String> perfis = keycloak.perfisDoUsuario(u.id()).stream()
                .map(KcPerfil::name)
                .filter(catalogo::containsKey)
                .sorted(Comparator.comparingInt(ordem::indexOf))
                .toList();

        String nome = ((u.firstName() == null ? "" : u.firstName()) + " "
                + (u.lastName() == null ? "" : u.lastName())).trim();
        if (nome.isEmpty()) {
            nome = u.username();
        }

        return new UsuarioResponse(u.id(), u.username(), nome, u.email(),
                Boolean.TRUE.equals(u.enabled()), perfis, totalMunicipios);
    }
}