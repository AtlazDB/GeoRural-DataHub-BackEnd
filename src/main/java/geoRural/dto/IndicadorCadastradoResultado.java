package geoRural.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Resultado de um indicador cadastrado (indicator_result). A regra desses indicadores não é versionada. */
public record IndicadorCadastradoResultado(
        String codigo,
        String nome,
        String unidade,
        BigDecimal valor,
        String status,
        Long versaoInsumoId,
        Long versaoImovelId,
        OffsetDateTime calculadoEm
) {
}
