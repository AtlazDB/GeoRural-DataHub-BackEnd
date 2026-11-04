package geoRural.indicator.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

//  Um indicador cadastrado, como está na indicator_definition. {@code parameters} e
// {@code ranges} voltam já convertidos do JSON da tabela.


public record IndicatorDefinitionResponse (
        String code,
        String name,
        String unit,
        String description,
        Long fonteId,
        String calculationModel,
        Map<String, Object> parameters,
        List<StatusRangeRequest> ranges,
        boolean active,
        OffsetDateTime createdAt
){
}
