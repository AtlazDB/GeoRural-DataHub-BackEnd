package geoRural.usuario.exception;

import java.util.Collection;
import java.util.TreeSet;

/** Código IBGE que não existe na malha municipal. Vira HTTP 400. */
public class MunicipioInvalidoException extends RuntimeException {
    public MunicipioInvalidoException(Collection<String> codigos) {
        super("Município não encontrado: " + String.join(", ", new TreeSet<>(codigos)) + ".");
    }
}
