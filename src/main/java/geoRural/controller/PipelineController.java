package geoRural.controller;

import geoRural.dto.CalculoIndicadoresResponse;
import geoRural.service.IndicadorService;
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

    // Dispara o pkg_indicadores. Hoje é chamado pela tela (ou por quem for agendar o cálculo
    // noturno); os indicadores ficam versionados, então rodar de novo não duplica nada.
    @Operation(summary = "Recalcula os indicadores de todos os imóveis")
    @PostMapping("/indicadores")
    public CalculoIndicadoresResponse calcularIndicadores() {
        return indicadorService.calcular();
    }
}
