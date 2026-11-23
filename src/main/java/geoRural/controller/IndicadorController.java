package geoRural.controller;

import geoRural.dto.IndicadorResponse;
import geoRural.dto.IndicadoresImovelResponse;
import geoRural.service.IndicadorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Indicadores de um imóvel: o valor atual de cada um (card da Consulta territorial) e o histórico
 * de cálculos (tela de Rastreabilidade).
 */
@RestController
@Tag(name = "Indicadores", description = "Indicadores ambientais do imóvel e o histórico de cada um")
@RequestMapping("/api/v1/imoveis/{codImovel}/indicadores")
public class IndicadorController {

    private final IndicadorService service;

    public IndicadorController(IndicadorService service) {
        this.service = service;
    }

    @Operation(summary = "Indicadores do imóvel, com a rastreabilidade do cálculo")
    @GetMapping
    public IndicadoresImovelResponse buscar(@PathVariable String codImovel) {
        return service.buscarPorImovel(codImovel);
    }

    @Operation(summary = "Histórico das versões de um indicador do imóvel")
    @GetMapping("/{indicador}/historico")
    public List<IndicadorResponse> historico(@PathVariable String codImovel, @PathVariable String indicador) {
        return service.historico(codImovel, indicador);
    }
}
