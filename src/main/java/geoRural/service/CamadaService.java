package geoRural.service;

import geoRural.dto.CamadaResponse;
import geoRural.dto.MapaImoveisResponse;

import java.util.List;

public interface CamadaService {

    /** As camadas com dados: a Reserva Legal e cada fonte ativa com carga no insumo genérico. */
    List<CamadaResponse> listar();

    /** As geometrias da camada na área visível, em GeoJSON (o mesmo formato do mapa de imóveis). */
    MapaImoveisResponse mapa(String id, Double minLon, Double minLat, Double maxLon, Double maxLat, int limite);
}
