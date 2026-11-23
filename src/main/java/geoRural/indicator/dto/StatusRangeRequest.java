package geoRural.indicator.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record StatusRangeRequest (
        @NotBlank String status,
        BigDecimal limit,
        Boolean includeLimit
) {
}
