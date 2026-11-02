package geoRural.service;

import geoRural.dto.ColetaResponse;
import geoRural.entity.Fonte;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem Spring e sem banco: o "Executar" só registra o pedido na fonte; quem coleta é a DAG coleta_fontes. */
class ColetaLinkServiceTest {

    private FonteService fontes;
    private FonteRepository repositorio;
    private ColetaLinkService coleta;

    @BeforeEach
    void preparar() {
        fontes = mock(FonteService.class);
        repositorio = mock(FonteRepository.class);
        coleta = new ColetaLinkServiceImpl(fontes, repositorio);
    }

    @Test
    void registraOPedidoParaOAirflow() {
        Fonte fonte = new Fonte();
        fonte.setUrlOrigem("https://geo.iat.pr.gov.br/arcgis/rest/services/Focos/FeatureServer/0/query");
        when(fontes.buscarPorId(7L)).thenReturn(fonte);

        ColetaResponse resposta = coleta.executar(7L);

        assertThat(resposta.resultado()).isEqualTo("SOLICITADA");
        assertThat(fonte.getColetaSolicitadaEm()).isNotNull();
        verify(repositorio).save(fonte);
    }

    @Test
    void fonteSemLinkERecusada() {
        Fonte fonte = new Fonte();
        fonte.setUrlOrigem("  ");
        when(fontes.buscarPorId(8L)).thenReturn(fonte);

        assertThatThrownBy(() -> coleta.executar(8L)).isInstanceOf(RequisicaoInvalidaException.class);
        verify(repositorio, never()).save(any());
    }
}
