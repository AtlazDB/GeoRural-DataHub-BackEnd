package geoRural.service;

import geoRural.dto.ColetaResponse;
import geoRural.entity.Fonte;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem Spring e sem banco: o "Executar" registra o pedido e dispara a DAG coletar_fonte, que é quem coleta. */
class ColetaLinkServiceTest {

    private FonteService fontes;
    private FonteRepository repositorio;
    private AirflowClient airflow;
    private ColetaLinkService coleta;

    @BeforeEach
    void preparar() {
        fontes = mock(FonteService.class);
        repositorio = mock(FonteRepository.class);
        airflow = mock(AirflowClient.class);
        coleta = new ColetaLinkServiceImpl(fontes, repositorio, airflow);
    }

    @Test
    void registraOPedidoEDisparaADag() {
        Fonte fonte = new Fonte();
        fonte.setUrlOrigem("https://geo.iat.pr.gov.br/arcgis/rest/services/Focos/FeatureServer/0/query");
        when(fontes.buscarPorId(7L)).thenReturn(fonte);

        ColetaResponse resposta = coleta.executar(7L);

        assertThat(resposta.resultado()).isEqualTo("SOLICITADA");
        assertThat(fonte.getColetaSolicitadaEm()).isNotNull();
        verify(repositorio).save(fonte);
        verify(airflow).disparar("coletar_fonte", Map.of("fonte_id", 7L, "pedido", true));
    }

    @Test
    void airflowForaDoArTiraOPedido() {
        Fonte fonte = new Fonte();
        fonte.setUrlOrigem("https://geo.iat.pr.gov.br/arcgis/rest/services/Focos/FeatureServer/0/query");
        when(fontes.buscarPorId(9L)).thenReturn(fonte);
        doThrow(new AirflowIndisponivelException("fora do ar")).when(airflow).disparar(anyString(), any());

        assertThatThrownBy(() -> coleta.executar(9L)).isInstanceOf(AirflowIndisponivelException.class);

        assertThat(fonte.getColetaSolicitadaEm()).isNull();
    }

    @Test
    void fonteSemLinkERecusada() {
        Fonte fonte = new Fonte();
        fonte.setUrlOrigem("  ");
        when(fontes.buscarPorId(8L)).thenReturn(fonte);

        assertThatThrownBy(() -> coleta.executar(8L)).isInstanceOf(RequisicaoInvalidaException.class);
        verify(repositorio, never()).save(any());
        verify(airflow, never()).disparar(anyString(), any());
    }
}
