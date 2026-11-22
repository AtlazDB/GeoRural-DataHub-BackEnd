package geoRural.exception;

/** Já existe um grupo de fontes com este nome. Vira HTTP 409. */
public class GrupoDuplicadoException extends RuntimeException {
    public GrupoDuplicadoException(String nome) {
        super("Já existe um grupo com o nome \"" + nome + "\".");
    }
}
