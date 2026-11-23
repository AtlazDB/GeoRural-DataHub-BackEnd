package geoRural.service;

import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.ArquivoNaoEncontradoException;
import geoRural.exception.ProcessamentoEmAndamentoException;
import geoRural.exception.ProcessamentoNaoEmAndamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem banco: o back-end não processa, só reserva o arquivo (de qualquer fonte) e dispara o Airflow. */
class ProcessamentoArquivoServiceTest {

    private JdbcTemplate jdbc;
    private AirflowClient airflow;
    private ProcessamentoArquivoService servico;

    @BeforeEach
    void preparar() {
        jdbc = mock(JdbcTemplate.class);
        airflow = mock(AirflowClient.class);
        servico = new ProcessamentoArquivoServiceImpl(jdbc, airflow);
    }

    @Test
    void reservaEDisparaADag() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);

        assertThat(servico.processar(1L).status()).isEqualTo("PROCESSANDO");

        verify(airflow).disparar("processar_arquivo", Map.of("arquivo_bruto_id", 1L, "reservado", true));
    }

    @Test
    void airflowForaDoArDevolveOArquivoParaRecebido() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(4L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(4L))).thenReturn(1);
        doThrow(new AirflowIndisponivelException("fora do ar")).when(airflow).disparar(anyString(), any());

        assertThatThrownBy(() -> servico.processar(4L)).isInstanceOf(AirflowIndisponivelException.class);

        verify(jdbc).update(contains("'RECEBIDO'"), eq(4L));
    }

    @Test
    void recusaQuandoJaEstaProcessando() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(2L))).thenReturn(0);

        assertThatThrownBy(() -> servico.processar(2L)).isInstanceOf(ProcessamentoEmAndamentoException.class);
        verify(airflow, never()).disparar(anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelarEncerraNoAirflowEMarcaRejeitado() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(5L))).thenReturn("PROCESSANDO");

        assertThat(servico.cancelar(5L).status()).isEqualTo("REJEITADO");

        InOrder ordem = inOrder(airflow, jdbc);
        ordem.verify(airflow).cancelar("processar_arquivo", "arquivo_bruto_id", 5L);
        ordem.verify(jdbc).update(contains("'FALHOU'"), eq(5L));
        ordem.verify(jdbc).update(contains("'REJEITADO'"), eq(5L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelarSemAirflowDeixaOArquivoProcessando() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(6L))).thenReturn("PROCESSANDO");
        doThrow(new AirflowIndisponivelException("fora do ar"))
                .when(airflow).cancelar(anyString(), anyString(), anyLong());

        assertThatThrownBy(() -> servico.cancelar(6L)).isInstanceOf(AirflowIndisponivelException.class);
        verify(jdbc, never()).update(anyString(), eq(6L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelarArquivoQueNaoEstaProcessandoDa409() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L))).thenReturn("PROCESSADO");

        assertThatThrownBy(() -> servico.cancelar(7L)).isInstanceOf(ProcessamentoNaoEmAndamentoException.class);
        verify(airflow, never()).cancelar(anyString(), anyString(), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelarArquivoQueNaoExisteDa404() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(8L))).thenReturn(null);

        assertThatThrownBy(() -> servico.cancelar(8L)).isInstanceOf(ArquivoNaoEncontradoException.class);
    }

    @Test
    void arquivoQueNaoExisteDa404() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(3L))).thenReturn(0);

        assertThatThrownBy(() -> servico.processar(3L)).isInstanceOf(ArquivoNaoEncontradoException.class);
        verify(jdbc, never()).update(anyString(), eq(3L));
    }
}
