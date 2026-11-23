package geoRural.exception;

/** O Airflow não respondeu, ou a DAG falhou: a ação não aconteceu, e o operador pode tentar de novo (503). */
public class AirflowIndisponivelException extends RuntimeException {
    public AirflowIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public AirflowIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
