package geoRural.controller;

import geoRural.dto.CamadaResponse;
import geoRural.dto.MapaImoveisResponse;
import geoRural.exception.ResourceNotFoundException;
import geoRural.service.CamadaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o comportamento do endpoint, não a matriz de perfis: sem os filtros do Spring Security.
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CamadaController.class)
class CamadaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CamadaService service;

    @Test
    void listaAsCamadas() throws Exception {
        when(service.listar()).thenReturn(List.of(
                new CamadaResponse("reserva-legal", "Reserva Legal (CAR)", "RESERVA_LEGAL", null)));

        mockMvc.perform(get("/api/v1/camadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("reserva-legal"));
    }

    @Test
    void devolveGeoJsonDaCamada() throws Exception {
        var feature = MapaImoveisResponse.Feature.de(Map.of("categoria", "APP"),
                "{\"type\":\"Point\",\"coordinates\":[-50.1,-25.1]}");
        when(service.mapa("fonte-12", -51.0, -26.0, -50.0, -25.0, 1000))
                .thenReturn(MapaImoveisResponse.de(List.of(feature), false, 1000));

        mockMvc.perform(get("/api/v1/camadas/fonte-12/mapa?minLon=-51&minLat=-26&maxLon=-50&maxLat=-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"));
    }

    @Test
    void camadaInexistenteResponde404() throws Exception {
        when(service.mapa(eq("xyz"), any(), any(), any(), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Camada não encontrada: xyz"));

        mockMvc.perform(get("/api/v1/camadas/xyz/mapa?minLon=-51&minLat=-26&maxLon=-50&maxLat=-25"))
                .andExpect(status().isNotFound());
    }
}
