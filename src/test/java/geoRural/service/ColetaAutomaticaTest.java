package geoRural.service;

import geoRural.entity.Fonte;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem Spring e sem banco: a verificação só chama o Executar das fontes vencidas. */
class ColetaAutomaticaTest {

    private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");

    private FonteRepository repositorio;
    private ColetaLinkService coleta;
    private ColetaAutomatica automatica;

    @BeforeEach
    void preparar() {
        repositorio = mock(FonteRepository.class);
        coleta = mock(ColetaLinkService.class);
        automatica = new ColetaAutomatica(repositorio, coleta, Clock.fixed(AGORA, AgendaColeta.FUSO));
    }

    private static Fonte fonte(long id, String periodicidade, int diasDesdeAUltima) {
        Fonte fonte = new Fonte();
        fonte.setId(id);
        fonte.setSigla("F" + id);
        fonte.setModalidade("LINK");
        fonte.setUrlOrigem("https://exemplo.gov.br/servico");
        fonte.setPeriodicidade(periodicidade);
        ReflectionTestUtils.setField(fonte, "ultimaColetaEm", OffsetDateTime.ofInstant(AGORA, AgendaColeta.FUSO).minusDays(diasDesdeAUltima));
        ReflectionTestUtils.setField(fonte, "ultimaColetaMensagem", "Coleta concluída");
        return fonte;
    }

    @Test
    void disparaSoAsVencidas() {
        when(repositorio.findAll()).thenReturn(List.of(
                fonte(1, "diária", 2),     // vencida
                fonte(2, "mensal", 10),    // ainda não
                fonte(3, "semanal", 8),    // vencida
                fonte(4, "eventual", 400)  // nunca
        ));

        assertThat(automatica.verificar()).isEqualTo(2);

        verify(coleta).executar(1L);
        verify(coleta).executar(3L);
        verify(coleta, times(2)).executar(anyLong());
    }

    @Test
    void nadaVencidoNaoChamaOAirflow() {
        when(repositorio.findAll()).thenReturn(List.of(fonte(1, "mensal", 3)));

        assertThat(automatica.verificar()).isZero();

        verify(coleta, never()).executar(anyLong());
    }

    @Test
    void airflowForaDoArParaAVerificacao() {
        when(repositorio.findAll()).thenReturn(List.of(fonte(1, "diária", 2), fonte(2, "diária", 2)));
        when(coleta.executar(1L)).thenThrow(new AirflowIndisponivelException("fora do ar"));

        assertThat(automatica.verificar()).isZero();

        verify(coleta, never()).executar(2L);
    }

    @Test
    void falhaDeUmaFonteNaoParaAsOutras() {
        when(repositorio.findAll()).thenReturn(List.of(fonte(1, "diária", 2), fonte(2, "diária", 2)));
        when(coleta.executar(1L)).thenThrow(new RequisicaoInvalidaException("desativada"));

        assertThat(automatica.verificar()).isEqualTo(1);

        verify(coleta).executar(2L);
    }

    @Test
    void cronInvalidoNumaFonteNaoParaAsOutras() {
        Fonte cronQuebrado = fonte(1, "diária", 2);
        cronQuebrado.setCron("todo dia");
        when(repositorio.findAll()).thenReturn(List.of(cronQuebrado, fonte(2, "diária", 2)));

        assertThat(automatica.verificar()).isEqualTo(1);

        verify(coleta, never()).executar(1L);
        verify(coleta).executar(2L);
    }
}
