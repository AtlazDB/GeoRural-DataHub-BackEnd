
package geoRural.dto;

import java.math.BigDecimal;

public record IndicadorMunicipalResponse(
        String codIbge,
        String municipio,
        String estado,
        String indicador,
        Long totalImoveis,
        BigDecimal areaReservaLegalHa,
        BigDecimal areaTotalImoveisHa,
        BigDecimal valorMunicipal,
        Long totalConformes,
        Long totalCriticos
) {
}
