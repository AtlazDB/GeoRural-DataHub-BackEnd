package geoRural.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import geoRural.dto.ProcessamentoResponse;
import geoRural.exception.ExclusaoNaoPermitidaException;
import geoRural.exception.ProcessamentoNaoEmAndamentoException;
import geoRural.service.ArquivoBrutoService;
import geoRural.service.ProcessamentoArquivoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArquivoController.class)
class ArquivoControllerTest {

    /** Jackson 2 local: o slice do Boot 4 expoe Jackson 3, e o projeto usa o 2. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessamentoArquivoService processamentoArquivoService;

    @MockitoBean
    private ArquivoBrutoService arquivoBrutoService;

    @Test
    void cancelarDevolveOArquivoRejeitado() throws Exception {
        when(processamentoArquivoService.cancelar(42L)).thenReturn(ProcessamentoResponse.cancelado(42L));

        mockMvc.perform(post("/api/v1/arquivos/42/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"));
    }

    @Test
    void cancelarArquivoParadoRespondeConflito() throws Exception {
        when(processamentoArquivoService.cancelar(9L)).thenThrow(new ProcessamentoNaoEmAndamentoException(9L));

        mockMvc.perform(post("/api/v1/arquivos/9/cancelar"))
                .andExpect(status().isConflict());
    }

    @Test
    void excluirRespondeSemConteudo() throws Exception {
        mockMvc.perform(delete("/api/v1/arquivos/62"))
                .andExpect(status().isNoContent());

        verify(arquivoBrutoService).excluirRejeitado(62L);
    }

    @Test
    void exclusaoNaoPermitidaRespondeConflitoComMensagem() throws Exception {
        doThrow(new ExclusaoNaoPermitidaException("Só arquivos rejeitados podem ser excluídos."))
                .when(arquivoBrutoService).excluirRejeitado(7L);

        String corpo = mockMvc.perform(delete("/api/v1/arquivos/7"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> resposta = MAPPER.readValue(corpo, new TypeReference<>() {});
        assertThat(resposta).containsKey("mensagem");
    }
}
