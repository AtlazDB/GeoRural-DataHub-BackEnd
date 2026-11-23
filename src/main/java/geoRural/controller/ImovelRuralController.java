package geoRural.controller;

import geoRural.dto.FiltroImoveis;
import geoRural.dto.ImovelResumoResponse;
import geoRural.dto.ImovelRuralResponse;
import geoRural.dto.MapaImoveisResponse;
import geoRural.dto.PaginaResponse;
import geoRural.entity.ImovelRural;
import geoRural.service.GeoJsonService;
import geoRural.service.ImovelRuralService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Imóveis rurais", description = "Imóveis do CAR: tabela, mapa e detalhe")
@RequestMapping("/api/v1/imoveis")
public class ImovelRuralController {

    private final ImovelRuralService service;
    private final GeoJsonService geoJsonService;

    public ImovelRuralController(
            ImovelRuralService service,
            GeoJsonService geoJsonService) {
        this.service = service;
        this.geoJsonService = geoJsonService;
    }

    // Filtros em ordem de precisão: codIbge (vínculo espacial) > municipio (nome
    // em texto, mantido por compatibilidade) > estado > todos.
    @Operation(summary = "Lista os imóveis com geometria (use sempre um filtro)")
    @GetMapping
    public List<ImovelRuralResponse> listar(
            @RequestParam(required = false) String codIbge,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String estado) {

        List<ImovelRural> imoveis;

        if (codIbge != null && !codIbge.isBlank()) {
            imoveis = service.listarPorCodIbge(codIbge);
        } else if (municipio != null) {
            imoveis = service.listarPorMunicipio(municipio);
        } else if (estado != null && !estado.isBlank()) {
            imoveis = service.listarPorEstado(estado);
        } else {
            imoveis = service.listarTodos();
        }

        return imoveis.stream()
                .map(this::toResponse)
                .toList();
    }

    /** Tabela: página sem geometria, com a caixa envolvente de cada imóvel para o front dar zoom. */
    @Operation(summary = "Página de imóveis sem geometria (tabela)")
    @GetMapping("/pagina")
    public PaginaResponse<ImovelResumoResponse> listarPagina(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String codImovel,
            @RequestParam(required = false) String situacao) {

        return service.listarPagina(new FiltroImoveis(municipio, codImovel, situacao), pagina, tamanho);
    }

    /**
     * Mapa (Leaflet): imóveis da área visível, em GeoJSON. O front chama de novo a cada movimento
     * do mapa, passando a caixa da tela. Com {@code truncado: true}, pedir para aproximar o zoom.
     */
    @Operation(summary = "Imóveis da área visível, em GeoJSON (mapa)")
    @GetMapping("/mapa")
    public MapaImoveisResponse mapa(
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String situacao,
            @RequestParam(defaultValue = "1000") int limite) {

        return service.listarNoMapa(minLon, minLat, maxLon, maxLat,
                new FiltroImoveis(municipio, null, situacao), limite);
    }

    @Operation(summary = "Um imóvel com a geometria completa")
    @GetMapping("/{codImovel}")
    public ImovelRuralResponse buscarPorCodigo(
            @PathVariable String codImovel) {

        ImovelRural imovel = service.buscarPorCodImovel(codImovel);

        return toResponse(imovel);
    }

    private ImovelRuralResponse toResponse(ImovelRural imovel) {
        return ImovelRuralResponse.de(
                imovel,
                geoJsonService.converter(imovel.getGeometria())

        );
    }
}