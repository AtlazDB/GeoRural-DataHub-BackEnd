package geoRural.service;

import geoRural.exception.ArquivoNaoEncontradoException;
import geoRural.exception.ProcessamentoEmAndamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem banco: o back-end não processa, só devolve o arquivo (de qualquer fonte) para a fila do Airflow. */
class ProcessamentoArquivoServiceTest {

    private JdbcTemplate jdbc;
    private ProcessamentoArquivoService servico;

    @BeforeEach
    void preparar() {
        jdbc = mock(JdbcTemplate.class);
        servico = new ProcessamentoArquivoServiceImpl(jdbc);
    }

    @Test
    void devolveParaAFilaDoAirflow() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);

        assertThat(servico.processar(1L).status()).isEqualTo("RECEBIDO");
    }

    @Test
    void recusaQuandoJaEstaProcessando() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(2L))).thenReturn(0);

        assertThatThrownBy(() -> servico.processar(2L)).isInstanceOf(ProcessamentoEmAndamentoException.class);
    }

    @Test
    void arquivoQueNaoExisteDa404() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(3L))).thenReturn(0);

        assertThatThrownBy(() -> servico.processar(3L)).isInstanceOf(ArquivoNaoEncontradoException.class);
        verify(jdbc, never()).update(anyString(), eq(3L));
    }
}
