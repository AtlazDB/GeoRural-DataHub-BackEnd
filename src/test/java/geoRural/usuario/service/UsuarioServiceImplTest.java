package geoRural.usuario.service;

import geoRural.usuario.dto.UsuarioResponse;
import geoRural.usuario.exception.OperacaoNaoPermitidaException;
import geoRural.usuario.exception.PerfilInvalidoException;
import geoRural.usuario.keycloak.KcPerfil;
import geoRural.usuario.keycloak.KcUsuario;
import geoRural.usuario.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceImplTest {

    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final UsuarioServiceImpl service = new UsuarioServiceImpl(keycloak);

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
}