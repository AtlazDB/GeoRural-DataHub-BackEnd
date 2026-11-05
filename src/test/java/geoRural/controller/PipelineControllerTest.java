package geoRural.controller;

import geoRural.exception.AirflowIndisponivelException;
import geoRural.service.IndicadorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /pipeline/indicadores: dispara a DAG calcular_indicadores e não espera o cálculo (US13.T10). */
@WebMvcTest(PipelineController.class)
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndicadorService indicadorService;

    @Test
    void calcularIndicadoresRespondeAceito() throws Exception {
        mockMvc.perform(post("/api/v1/pipeline/indicadores"))
                .andExpect(status().isAccepted());

        verify(indicadorService).calcular();
    }

    @Test
    void airflowForaDoArRespondeIndisponivel() throws Exception {
        doThrow(new AirflowIndisponivelException("A DAG calcular_indicadores está pausada no Airflow: ative-a e tente de novo."))
                .when(indicadorService).calcular();

        mockMvc.perform(post("/api/v1/pipeline/indicadores"))
                .andExpect(status().isServiceUnavailable());
    }
}
