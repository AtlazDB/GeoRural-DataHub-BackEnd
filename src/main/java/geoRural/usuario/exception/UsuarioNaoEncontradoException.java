package geoRural.usuario.exception;

/** Não existe usuário com esse id no Keycloak. Vira HTTP 404. */
public class UsuarioNaoEncontradoException extends RuntimeException {
    public UsuarioNaoEncontradoException(String id) {
        super("Usuário não encontrado: " + id);
    }
}