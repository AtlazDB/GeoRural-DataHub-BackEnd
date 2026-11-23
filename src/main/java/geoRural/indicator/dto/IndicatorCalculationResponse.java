package geoRural.indicator.dto;

import java.time.OffsetDateTime;

/**
 * Um disparo do cálculo de um indicador (indicator_calculation, V29), para a barra de progresso.
 * status: NA_FILA, EM_ANDAMENTO, CONCLUIDO ou ERRO. progress de 0 a 100; progressStep é a etapa
 * atual (LEITURA, CALCULO, GRAVACAO). errorMessage só no ERRO.
 */
public record IndicatorCalculationResponse(
        Long id,
        String indicatorCode,
        String status,
        int progress,
        String progressStep,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorMessage
) {
}
