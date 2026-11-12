package geoRural.usuario.exception;

/** Login ou e-mail que já existe no Keycloak. Vira HTTP 409. */
public class UsuarioDuplicadoException extends RuntimeException {
    public UsuarioDuplicadoException() {
        super("Já existe um usuário com este login ou e-mail.");
    }
}
