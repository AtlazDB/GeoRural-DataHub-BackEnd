package geoRural.indicator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

// Cadastro de um indicador. Aqui só fica o que dá para conferir campo a campo; as regras que
// olham o banco ou a lista inteira (sigla única, fonte com dados, tipo de geometria, ordem das
// faixas) ficam no service.
// {@code parameters} e {@code ranges} viram JSON na indicator_definition e são lidos pela DAG
// de cálculo (GeoPandas): os nomes dos campos são o contrato entre os dois lados

public record IndicatorDefinitionRequest (
        @NotBlank @Size(max = 10) String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 12) String unit,
        @Size(max = 1000) String description,
        @NotNull Long fonteId,
        @NotBlank String calculationModel,
        Map<String, Object> parameters,
        @NotEmpty List<@Valid StatusRangeRequest> ranges
) {
}
