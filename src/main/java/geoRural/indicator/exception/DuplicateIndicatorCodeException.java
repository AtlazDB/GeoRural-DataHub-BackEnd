package geoRural.indicator.exception;

// Já existe um indicador cadastrado com a sigla (mesmo desativado: a sigla não volta a ficar livre).
public class DuplicateIndicatorCodeException extends RuntimeException {
    public DuplicateIndicatorCodeException(String code) {
        super("Já existe um indicador com a sigla \"" + code + "\".");
    }
}
