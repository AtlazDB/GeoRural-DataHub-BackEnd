package geoRural.usuario.exception;

/** Já existe um perfil (role) com este nome no Keycloak. Vira HTTP 409. */
public class PerfilDuplicadoException extends RuntimeException {
    public PerfilDuplicadoException(String nome) {
        super("Já existe um perfil com o nome " + nome + ".");
    }
}
