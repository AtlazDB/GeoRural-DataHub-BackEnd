package geoRural.indicator.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.IndicatorResultResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import geoRural.indicator.exception.DuplicateIndicatorCodeException;
import geoRural.indicator.service.IndicatorDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As rotas /api/v1/indicators: códigos HTTP e formato dos erros. As regras estão no IndicatorDefinitionServiceTest. */
@WebMvcTest(IndicatorDefinitionController.class)
class IndicatorDefinitionControllerTest {

    /** Jackson 2 local: o slice do Boot 4 expoe Jackson 3, e o projeto usa o 2. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID_BODY = """
            {"code": "DFOCO", "name": "Densidade de focos", "unit": "focos/1000ha", "fonteId": 7,
             "calculationModel": "POINT_DENSITY", "parameters": {"factor": 1000},
             "ranges": [{"status": "CONFORME", "limit": 5, "includeLimit": true}, {"status": "CRITICO"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndicatorDefinitionService service;

    @Test
    void createRespondsCreatedWithTheIndicator() throws Exception {
        when(service.create(any())).thenReturn(saved());

        Map<String, Object> resposta = body(postJson("/api/v1/indicators", VALID_BODY), status().isCreated());

        assertThat(resposta).containsEntry("code", "DFOCO").containsEntry("active", true);
        verify(service).create(new IndicatorDefinitionRequest("DFOCO", "Densidade de focos", "focos/1000ha", null,
                7L, "POINT_DENSITY", Map.of("factor", 1000),
                List.of(new StatusRangeRequest("CONFORME", new BigDecimal("5"), true),
                        new StatusRangeRequest("CRITICO", null, null))));
    }

    @Test
    void createWithBlankFieldsRespondsBadRequestWithTheFields() throws Exception {
        Map<String, Object> resposta = body(postJson("/api/v1/indicators", """
                {"code": "DFOCO", "name": "", "unit": "%", "calculationModel": "AREA_COVERAGE",
                 "ranges": [{"status": ""}]}
                """), status().isBadRequest());

        assertThat(resposta).containsKey("mensagem");
        assertThat((List<Object>) resposta.get("campos")).containsExactlyInAnyOrder("name", "fonteId", "ranges");
        verify(service, never()).create(any());
    }

    @Test
    void createWithUnreadableJsonRespondsBadRequest() throws Exception {
        Map<String, Object> resposta = body(postJson("/api/v1/indicators", """
                {"code": "DFOCO", "ranges": [{"status": "CONFORME", "limit": "abc"}]}
                """), status().isBadRequest());

        assertThat(resposta).containsKey("mensagem");
        verify(service, never()).create(any());
    }

    @Test
    void ruleBrokenInTheServiceRespondsBadRequestWithTheField() throws Exception {
        when(service.create(any())).thenThrow(
                new RequisicaoInvalidaException("Faixa 2: o limite tem que ser maior.", List.of("ranges")));

        Map<String, Object> resposta = body(postJson("/api/v1/indicators", VALID_BODY), status().isBadRequest());

        assertThat(resposta).containsEntry("mensagem", "Faixa 2: o limite tem que ser maior.");
        assertThat((List<Object>) resposta.get("campos")).containsExactly("ranges");
    }

    @Test
    void duplicateCodeRespondsConflict() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateIndicatorCodeException("DFOCO"));

        Map<String, Object> resposta = body(postJson("/api/v1/indicators", VALID_BODY), status().isConflict());

        assertThat(resposta).containsEntry("mensagem", "Já existe um indicador com a sigla \"DFOCO\".");
    }

    @Test
    void findAllRespondsTheList() throws Exception {
        when(service.findAll()).thenReturn(List.of(saved()));

        String corpo = mockMvc.perform(get("/api/v1/indicators"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> lista = MAPPER.readValue(corpo, new TypeReference<>() {});
        assertThat(lista).extracting(i -> i.get("code")).containsExactly("DFOCO");
    }

    @Test
    void findByCodeMissingRespondsNotFound() throws Exception {
        when(service.findByCode("NADA")).thenThrow(new ResourceNotFoundException("Indicador \"NADA\" não encontrado."));

        mockMvc.perform(get("/api/v1/indicators/NADA"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateRespondsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/indicators/DFOCO/deactivate"))
                .andExpect(status().isNoContent());

        verify(service).deactivate("DFOCO");
    }

    @Test
    void deactivateMissingRespondsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Indicador \"NADA\" não encontrado.")).when(service).deactivate("NADA");

        mockMvc.perform(post("/api/v1/indicators/NADA/deactivate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void calculateRespondsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/indicators/DFOCO/calculate"))
                .andExpect(status().isAccepted());

        verify(service).calculate("DFOCO");
    }

    @Test
    void calculateDeactivatedRespondsBadRequest() throws Exception {
        doThrow(new RequisicaoInvalidaException("O indicador \"DFOCO\" está desativado e não é mais calculado."))
                .when(service).calculate("DFOCO");

        Map<String, Object> resposta = body(post("/api/v1/indicators/DFOCO/calculate"), status().isBadRequest());

        assertThat(resposta).containsEntry("mensagem", "O indicador \"DFOCO\" está desativado e não é mais calculado.");
    }

    @Test
    void calculateWithAirflowDownRespondsServiceUnavailable() throws Exception {
        doThrow(new AirflowIndisponivelException("A DAG calcular_indicador está pausada no Airflow: ative-a e tente de novo."))
                .when(service).calculate("DFOCO");

        mockMvc.perform(post("/api/v1/indicators/DFOCO/calculate"))
                .andExpect(status().isServiceUnavailable());
    }

    // ---- resultado (US13.T08)

    @Test
    void findLatestResultRespondsOkWithTheResult() throws Exception {
        when(service.findLatestResult("DFOCO", "SP-1")).thenReturn(new IndicatorResultResponse("DFOCO", "SP-1",
                new BigDecimal("19.9005"), "ALERTA", new BigDecimal("2"), null,
                OffsetDateTime.parse("2026-11-05T12:00:00Z")));

        Map<String, Object> resposta = body(get("/api/v1/indicators/DFOCO/results").param("codImovel", "SP-1"),
                status().isOk());

        assertThat(resposta)
                .containsEntry("indicatorCode", "DFOCO")
                .containsEntry("codImovel", "SP-1")
                .containsEntry("status", "ALERTA")
                .containsEntry("value", 19.9005)
                .containsEntry("areaImovelHa", null);
    }

    @Test
    void findLatestResultNotCalculatedRespondsNotFound() throws Exception {
        when(service.findLatestResult("DFOCO", "SP-1")).thenThrow(new ResourceNotFoundException(
                "O indicador \"DFOCO\" ainda não foi calculado para o imóvel SP-1."));

        Map<String, Object> resposta = body(get("/api/v1/indicators/DFOCO/results").param("codImovel", "SP-1"),
                status().isNotFound());

        assertThat(resposta).containsEntry("mensagem", "O indicador \"DFOCO\" ainda não foi calculado para o imóvel SP-1.");
    }

    @Test
    void findLatestResultWithoutCodImovelRespondsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/indicators/DFOCO/results"))
                .andExpect(status().isBadRequest());

        verify(service, never()).findLatestResult(any(), any());
    }

    private Map<String, Object> body(
            MockHttpServletRequestBuilder request,
            ResultMatcher expected) throws Exception {
        String corpo = mockMvc.perform(request)
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readValue(corpo, new TypeReference<>() {});
    }

    private static MockHttpServletRequestBuilder postJson(
            String url, String json) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static IndicatorDefinitionResponse saved() {
        return new IndicatorDefinitionResponse("DFOCO", "Densidade de focos", "focos/1000ha", null, 7L,
                "POINT_DENSITY", Map.of("factor", 1000),
                List.of(new StatusRangeRequest("CONFORME", new BigDecimal("5"), true),
                        new StatusRangeRequest("CRITICO", null, null)),
                true, OffsetDateTime.parse("2026-11-03T12:00:00Z"));
    }
}
