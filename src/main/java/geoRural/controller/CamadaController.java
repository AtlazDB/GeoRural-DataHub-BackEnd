package geoRural.controller;

import geoRural.dto.CamadaResponse;
import geoRural.dto.MapaImoveisResponse;
import geoRural.service.CamadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Camadas extras do mapa da consulta: Reserva Legal e as fontes do insumo genérico. */
@Tag(name = "Camadas do mapa")
@RestController
@RequestMapping("/api/v1/camadas")
public class CamadaController {

    private final CamadaService service;

    public CamadaController(CamadaService service) {
        this.service = service;
    }

    @Operation(summary = "Camadas que o mapa pode ligar")
    @GetMapping
    public List<CamadaResponse> listar() {
        return service.listar();
    }

    /**
     * Geometrias da camada na área visível, em GeoJSON. O front chama de novo a cada movimento do
     * mapa, passando a caixa da tela. Com {@code truncado: true}, pedir para aproximar o zoom.
     */
    @Operation(summary = "Geometrias da camada na área visível, em GeoJSON (mapa)")
    @GetMapping("/{id}/mapa")
    public MapaImoveisResponse mapa(
            @PathVariable String id,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(defaultValue = "1000") int limite) {
        return service.mapa(id, minLon, minLat, maxLon, maxLat, limite);
    }
}
