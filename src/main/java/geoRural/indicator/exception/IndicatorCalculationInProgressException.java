package geoRural.indicator.exception;

// O indicador já tem um cálculo na fila ou em andamento: um segundo disparo só repetiria o mesmo trabalho.
public class IndicatorCalculationInProgressException extends RuntimeException {
    public IndicatorCalculationInProgressException(String code) {
        super("O indicador \"" + code + "\" já está sendo calculado. Espere terminar para calcular de novo.");
    }
}
