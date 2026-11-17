package geoRural.exception;

public class ProcessamentoNaoEmAndamentoException extends RuntimeException {
    public ProcessamentoNaoEmAndamentoException(Long arquivoId) {
        super("O arquivo " + arquivoId + " não está sendo processado: não há o que cancelar.");
    }
}
