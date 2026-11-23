package geoRural.controller;

import geoRural.dto.FocosNoMapaResponse;
import geoRural.service.FocoService;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Focos de calor da área visível do mapa, os mesmos que o IFC conta. */
@RestController
@Tag(name = "Focos de calor", description = "Focos de calor para o mapa")
@RequestMapping("/api/v1/focos")
public class FocoController {

    private final FocoService service;

    public FocoController(FocoService service) {
        this.service = service;
    }

    @Operation(summary = "Focos de calor da área visível do mapa")
    @GetMapping
    public FocosNoMapaResponse listar(
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(defaultValue = "2000") int limite) {

        return service.listarNoMapa(minLon, minLat, maxLon, maxLat, limite);
    }
}
