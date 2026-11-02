package geoRural.exception;

/**
 * A revalidação recusou gravar o registro na Zona Tratada: já encerrado, fonte sem mapeamento ou
 * dado mais novo já gravado. A mensagem vem da DAG revalidar_registro, escrita para o operador.
 */
public class GravacaoNaoPermitidaException extends RuntimeException {
    public GravacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}
