package geoRural.usuario.service;

import geoRural.usuario.dto.MunicipioLiberado;
import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.NovoPerfilRequest;
import geoRural.usuario.dto.PerfilResponse;
import geoRural.usuario.dto.NovoUsuarioRequest;
import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.exception.MunicipioInvalidoException;
import geoRural.usuario.exception.OperacaoNaoPermitidaException;
import geoRural.usuario.exception.PerfilDuplicadoException;
import geoRural.usuario.exception.PerfilInvalidoException;
import geoRural.usuario.exception.UsuarioNaoEncontradoException;
import geoRural.usuario.keycloak.KcPerfil;
import geoRural.usuario.keycloak.KcPerfilCompleto;
import geoRural.usuario.keycloak.KcUsuario;
import geoRural.usuario.keycloak.KeycloakAdminClient;
import geoRural.usuario.repository.UsuarioMunicipioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioServiceImplTest {

    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final UsuarioMunicipioRepository municipios = mock(UsuarioMunicipioRepository.class);
    private final UsuarioServiceImpl service = new UsuarioServiceImpl(keycloak, municipios);

    @Test
    void gestorNaoPodeRemoverOProprioPerfilGestor() {
        assertThrows(OperacaoNaoPermitidaException.class,
                () -> service.definirPerfis("u1", Set.of("ANALISTA"), "u1"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void perfilForaDoCatalogoERecusado() {
        assertThrows(PerfilInvalidoException.class,
                () -> service.definirPerfis("u2", Set.of("realm-admin"), "u1"));
        // Só consultou o catálogo: não mexeu em nenhum usuário.
        verify(keycloak, never()).buscarUsuario(any());
        verify(keycloak, never()).atribuirPerfil(any(), any());
    }

    @Test
    void perfilCombinadoCriadoPelaTelaPodeSerAtribuido() {
        KcPerfil coordenador = new KcPerfil("9", "COORDENADOR");
        when(keycloak.listarPerfisDoRealm()).thenReturn(List.of(
                new KcPerfilCompleto("9", "COORDENADOR", "Analista + Auditor", true, Map.of("georural", List.of("perfil"))),
                new KcPerfilCompleto("8", "offline_access", null, false, Map.of())));
        when(keycloak.compostosDoPerfil("COORDENADOR"))
                .thenReturn(List.of(new KcPerfil("1", "ANALISTA"), new KcPerfil("2", "AUDITOR")));
        when(keycloak.buscarUsuario("u2"))
                .thenReturn(new KcUsuario("u2", "ana", "ana@x.com", "Ana", "Silva", true));
        when(keycloak.perfisDoUsuario("u2")).thenReturn(List.of(), List.of(coordenador));
        when(keycloak.buscarPerfil("COORDENADOR")).thenReturn(coordenador);

        UsuarioResponse r = service.definirPerfis("u2", Set.of("COORDENADOR"), "u1");

        verify(keycloak).atribuirPerfil("u2", coordenador);
        assertEquals(List.of("COORDENADOR"), r.perfis());
    }

    @Test
    void gestorPodeTrocarOGestorPorUmCombinadoQueOInclui() {
        when(keycloak.listarPerfisDoRealm()).thenReturn(List.of(
                new KcPerfilCompleto("9", "DIRETOR", null, true, Map.of("georural", List.of("perfil")))));
        when(keycloak.compostosDoPerfil("DIRETOR")).thenReturn(List.of(new KcPerfil("4", "GESTOR")));
        when(keycloak.buscarUsuario("u1"))
                .thenReturn(new KcUsuario("u1", "eu", "eu@x.com", "Eu", "Mesmo", true));

        assertDoesNotThrow(() -> service.definirPerfis("u1", Set.of("DIRETOR"), "u1"));
    }

    @Test
    void listaOsQuatroDoSistemaEOsCombinados() {
        when(keycloak.listarPerfisDoRealm()).thenReturn(List.of(
                new KcPerfilCompleto("9", "COORDENADOR", "Analista + Auditor", true, Map.of("georural", List.of("perfil"))),
                new KcPerfilCompleto("7", "default-roles-georural", null, true, null)));
        when(keycloak.compostosDoPerfil("COORDENADOR"))
                .thenReturn(List.of(new KcPerfil("2", "AUDITOR"), new KcPerfil("1", "ANALISTA")));

        List<PerfilResponse> perfis = service.listarPerfis();

        assertEquals(List.of("OPERADOR_DADOS", "ANALISTA", "AUDITOR", "GESTOR", "COORDENADOR"),
                perfis.stream().map(PerfilResponse::codigo).toList());
        PerfilResponse coordenador = perfis.get(4);
        assertFalse(coordenador.doSistema());
        assertEquals(List.of("ANALISTA", "AUDITOR"), coordenador.inclui());
        assertEquals("Analista + Auditor", coordenador.descricao());
    }

    @Test
    void criarPerfilCriaARoleEJuntaOsPerfisDoSistema() {
        KcPerfil analista = new KcPerfil("1", "ANALISTA");
        KcPerfil auditor = new KcPerfil("2", "AUDITOR");
        when(keycloak.buscarPerfil("ANALISTA")).thenReturn(analista);
        when(keycloak.buscarPerfil("AUDITOR")).thenReturn(auditor);

        PerfilResponse r = service.criarPerfil(
                new NovoPerfilRequest(" coordenador ", "Analista + Auditor", Set.of("AUDITOR", "ANALISTA")));

        verify(keycloak).criarPerfil("COORDENADOR", "Analista + Auditor");
        verify(keycloak).adicionarCompostos("COORDENADOR", List.of(analista, auditor));
        assertEquals("COORDENADOR", r.codigo());
        assertEquals(List.of("ANALISTA", "AUDITOR"), r.inclui());
    }

    @Test
    void criarPerfilComNomeDeUmDoSistemaOuIncluindoOutroCombinadoERecusado() {
        assertThrows(PerfilDuplicadoException.class,
                () -> service.criarPerfil(new NovoPerfilRequest("gestor", null, Set.of("ANALISTA"))));
        assertThrows(PerfilInvalidoException.class,
                () -> service.criarPerfil(new NovoPerfilRequest("CHEFE", null, Set.of("COORDENADOR"))));
        verify(keycloak, never()).criarPerfil(any(), any());
    }

    @Test
    void aplicaSomenteADiferencaEIgnoraRolesDoKeycloak() {
        KcPerfil padrao = new KcPerfil("0", "default-roles-georural");
        KcPerfil analista = new KcPerfil("1", "ANALISTA");
        KcPerfil auditor = new KcPerfil("2", "AUDITOR");

        when(keycloak.buscarUsuario("u2"))
                .thenReturn(new KcUsuario("u2", "ana", "ana@x.com", "Ana", "Silva", true));
        when(keycloak.perfisDoUsuario("u2"))
                .thenReturn(List.of(padrao, analista), List.of(padrao, auditor));
        when(keycloak.buscarPerfil("AUDITOR")).thenReturn(auditor);

        UsuarioResponse r = service.definirPerfis("u2", Set.of("AUDITOR"), "u1");

        verify(keycloak).atribuirPerfil("u2", auditor);
        verify(keycloak).removerPerfil("u2", analista);
        verify(keycloak, never()).removerPerfil("u2", padrao);
        assertEquals(List.of("AUDITOR"), r.perfis());
        assertEquals("Ana Silva", r.nome());
    }

    @Test
    void municipioInexistenteERecusadoSemGravar() {
        when(municipios.inexistentes(Set.of("9999999"))).thenReturn(Set.of("9999999"));

        assertThrows(MunicipioInvalidoException.class,
                () -> service.definirMunicipios("u2", new MunicipiosRequest(false, Set.of("9999999")), "gestor"));
        verify(municipios, never()).substituir(any(), any(), any());
    }

    @Test
    void todasLimpaALista() {
        when(keycloak.buscarUsuario("u2")).thenReturn(new KcUsuario("u2", "ana", null, null, null, true));
        when(municipios.inexistentes(Set.of())).thenReturn(Set.of());
        when(municipios.listar("u2")).thenReturn(List.of());

        MunicipiosResponse r = service.definirMunicipios("u2", new MunicipiosRequest(true, Set.of("4102901")), "gestor");

        verify(municipios).substituir("u2", Set.of(), "gestor");
        assertTrue(r.todas());
    }

    @Test
    void listaComCidadesGravaOsCodigosSemEspacos() {
        when(keycloak.buscarUsuario("u2")).thenReturn(new KcUsuario("u2", "ana", null, null, null, true));
        when(municipios.inexistentes(Set.of("4102901", "4119905"))).thenReturn(Set.of());
        when(municipios.listar("u2")).thenReturn(List.of(new MunicipioLiberado("4102901", "Bituruna"),
                new MunicipioLiberado("4119905", "Ponta Grossa")));

        MunicipiosResponse r = service.definirMunicipios("u2",
                new MunicipiosRequest(false, Set.of(" 4102901", "4119905 ")), "gestor");

        verify(municipios).substituir("u2", Set.of("4102901", "4119905"), "gestor");
        assertFalse(r.todas());
        assertEquals(2, r.municipios().size());
    }

    @Test
    void gestorNaoPodeDesativarASiMesmo() {
        assertThrows(OperacaoNaoPermitidaException.class, () -> service.alterarAtivo("u1", false, "u1"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void desativaNoKeycloakEDevolveOUsuarioAtualizado() {
        when(keycloak.buscarUsuario("u2"))
                .thenReturn(new KcUsuario("u2", "ana", "ana@x.com", "Ana", "Silva", false));
        when(keycloak.perfisDoUsuario("u2")).thenReturn(List.of());

        UsuarioResponse r = service.alterarAtivo("u2", false, "u1");

        verify(keycloak).alterarAtivo("u2", false);
        assertFalse(r.ativo());
    }

    @Test
    void editaADescricaoDeUmCombinado() {
        when(keycloak.listarPerfisDoRealm()).thenReturn(List.of(
                new KcPerfilCompleto("9", "COORDENADOR", "antiga", true, Map.of("georural", List.of("perfil")))));
        when(keycloak.compostosDoPerfil("COORDENADOR"))
                .thenReturn(List.of(new KcPerfil("1", "ANALISTA"), new KcPerfil("2", "AUDITOR")));

        PerfilResponse r = service.editarDescricaoPerfil("coordenador", "  Acompanha a equipe  ");

        verify(keycloak).atualizarDescricaoPerfil("COORDENADOR", "Acompanha a equipe");
        assertEquals("Acompanha a equipe", r.descricao());
        assertEquals(List.of("ANALISTA", "AUDITOR"), r.inclui());
    }

    @Test
    void naoEditaPerfilDoSistemaNemRoleQueNaoFoiCriadaPelaTela() {
        assertThrows(OperacaoNaoPermitidaException.class, () -> service.editarDescricaoPerfil("GESTOR", "x"));
        when(keycloak.listarPerfisDoRealm()).thenReturn(List.of(
                new KcPerfilCompleto("7", "offline_access", null, false, null)));
        assertThrows(PerfilInvalidoException.class, () -> service.editarDescricaoPerfil("offline_access", "x"));
        verify(keycloak, never()).atualizarDescricaoPerfil(any(), any());
    }

    @Test
    void redefineASenhaComoProvisoria() {
        when(keycloak.buscarUsuario("u2"))
                .thenReturn(new KcUsuario("u2", "ana", "ana@x.com", "Ana", "Silva", true));

        service.redefinirSenha("u2", "novaSenha123");

        verify(keycloak).redefinirSenha("u2", "novaSenha123");
    }

    @Test
    void naoRedefineSenhaDeUsuarioQueNaoExiste() {
        when(keycloak.buscarUsuario("nada")).thenThrow(new UsuarioNaoEncontradoException("nada"));

        assertThrows(UsuarioNaoEncontradoException.class, () -> service.redefinirSenha("nada", "novaSenha123"));
        verify(keycloak, never()).redefinirSenha(any(), any());
    }

    @Test
    void criarComPerfilInvalidoNaoCriaNadaNoKeycloak() {
        assertThrows(PerfilInvalidoException.class, () -> service.criar(novo(Set.of("realm-admin"), false, Set.of()), "gestor"));
        verify(keycloak, never()).criarUsuario(any(), any(), any(), any(), any());
    }

    @Test
    void criarCadastraNoKeycloakAtribuiOsPerfisEGravaAsCidades() {
        KcPerfil analista = new KcPerfil("1", "ANALISTA");
        when(municipios.inexistentes(Set.of("4102901"))).thenReturn(Set.of());
        when(keycloak.criarUsuario("ana", "ana@x.com", "Ana", "Silva", "provisoria1")).thenReturn("u9");
        when(keycloak.buscarPerfil("ANALISTA")).thenReturn(analista);
        when(keycloak.buscarUsuario("u9")).thenReturn(new KcUsuario("u9", "ana", "ana@x.com", "Ana", "Silva", true));
        when(keycloak.perfisDoUsuario("u9")).thenReturn(List.of(analista));
        when(municipios.codigos("u9")).thenReturn(Set.of("4102901"));

        UsuarioResponse r = service.criar(novo(Set.of("ANALISTA"), false, Set.of("4102901")), "gestor");

        verify(keycloak).atribuirPerfil("u9", analista);
        verify(municipios).substituir("u9", Set.of("4102901"), "gestor");
        assertEquals(List.of("ANALISTA"), r.perfis());
        assertEquals(1, r.totalMunicipios());
    }

    private static NovoUsuarioRequest novo(Set<String> perfis, boolean todas, Set<String> cidades) {
        return new NovoUsuarioRequest(" ana ", "Ana", "Silva", "ana@x.com", "provisoria1", perfis, todas, cidades);
    }
}
