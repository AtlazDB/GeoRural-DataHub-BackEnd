package geoRural.usuario.exception;

/** O Keycloak não respondeu ou recusou a credencial técnica. Vira HTTP 502. */
public class KeycloakIndisponivelException extends RuntimeException {
    public KeycloakIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}