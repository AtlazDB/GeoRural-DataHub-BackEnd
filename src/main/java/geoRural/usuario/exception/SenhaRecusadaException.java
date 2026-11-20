package geoRural.usuario.exception;

/** O Keycloak recusou a senha nova (regras de senha do realm). Vira HTTP 400. */
public class SenhaRecusadaException extends RuntimeException {
    public SenhaRecusadaException() {
        super("O Keycloak recusou a senha: ela não atende às regras de senha. Tente uma mais longa ou use o botão Gerar.");
    }
}
