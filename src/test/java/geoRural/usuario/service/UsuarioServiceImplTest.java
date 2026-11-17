package geoRural.usuario.service;

import geoRural.usuario.dto.MunicipioLiberado;
import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.NovoUsuarioRequest;
import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.exception.MunicipioInvalidoException;
import geoRural.usuario.exception.OperacaoNaoPermitidaException;
import geoRural.usuario.exception.PerfilInvalidoException;
import geoRural.usuario.keycloak.KcPerfil;
import geoRural.usuario.keycloak.KcUsuario;
import geoRural.usuario.keycloak.KeycloakAdminClient;
import geoRural.usuario.repository.UsuarioMunicipioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void perfilForaDosQuatroDoSistemaERecusado() {
        assertThrows(PerfilInvalidoException.class,
                () -> service.definirPerfis("u2", Set.of("realm-admin"), "u1"));
        verifyNoInteractions(keycloak);
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
    void criarComPerfilInvalidoNaoChegaAoKeycloak() {
        assertThrows(PerfilInvalidoException.class, () -> service.criar(novo(Set.of("realm-admin"), false, Set.of()), "gestor"));
        verifyNoInteractions(keycloak);
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
