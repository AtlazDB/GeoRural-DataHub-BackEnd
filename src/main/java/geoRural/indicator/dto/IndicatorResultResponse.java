package geoRural.indicator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// O resultado de um indicador para um imóvel, como a DAG gravou na indicator_result.
// value, numerator e areaImovelHa podem vir null.


public record IndicatorResultResponse(
        String indicatorCode,
        String codImovel,
        BigDecimal value,
        String status,
        BigDecimal numerator,
        BigDecimal areaImovelHa,
        OffsetDateTime calculatedAt
) {
}
