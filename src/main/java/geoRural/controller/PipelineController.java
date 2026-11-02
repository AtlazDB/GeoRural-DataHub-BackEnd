package geoRural.controller;

import geoRural.dto.CalculoIndicadoresResponse;
import geoRural.service.IndicadorService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private final IndicadorService indicadorService;

    public PipelineController(IndicadorService indicadorService) {
        this.indicadorService = indicadorService;
    }

    // Dispara o pkg_indicadores. Hoje é chamado pela tela (ou por quem for agendar o cálculo
    // noturno); os indicadores ficam versionados, então rodar de novo não duplica nada.
    @PostMapping("/indicadores")
    public CalculoIndicadoresResponse calcularIndicadores() {
        return indicadorService.calcular();
    }
}
