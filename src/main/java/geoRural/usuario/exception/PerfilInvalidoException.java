package geoRural.usuario.exception;

/** Perfil fora dos 4 permitidos no GeoRural. Vira HTTP 400. */
public class PerfilInvalidoException extends RuntimeException {
    public PerfilInvalidoException(String perfil) {
        super("Perfil inválido: " + perfil);
    }
}