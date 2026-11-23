package geoRural.controller;

import geoRural.service.IndicadorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Pipeline", description = "Disparos manuais do pipeline")
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private final IndicadorService indicadorService;

    public PipelineController(IndicadorService indicadorService) {
        this.indicadorService = indicadorService;
    }

    // Dispara a DAG calcular_indicadores (IRL e IFC em Python, no Airflow) e volta logo: 202, e os
    // valores novos aparecem em alguns minutos. 503 se o Airflow estiver fora do ar ou a DAG pausada.
    // Os indicadores ficam versionados, então rodar de novo não duplica nada.
    @Operation(summary = "Recalcula os indicadores de todos os imóveis (dispara a DAG no Airflow)")
    @PostMapping("/indicadores")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void calcularIndicadores() {
        indicadorService.calcular();
    }
}
