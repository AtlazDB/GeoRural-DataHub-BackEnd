package geoRural.indicator.dto;

import java.math.BigDecimal;

public record IndicatorMunicipalityResponse(
        String codIbge,
        String name,
        String state,
        int properties,
        int conforme,
        int alerta,
        int critico,
        int validacao,
        BigDecimal averageValue,
        BigDecimal numeratorSum
) {
}
