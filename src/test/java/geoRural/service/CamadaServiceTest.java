package geoRural.service;

import geoRural.dto.CamadaResponse;
import geoRural.dto.MapaImoveisResponse;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.repository.CamadaRepository;
import geoRural.repository.CamadaRepository.FonteInsumo;
import geoRural.repository.CamadaRepository.InsumoNoMapa;
import geoRural.repository.CamadaRepository.ReservaNoMapa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sem banco: o repositório é mock, a conversão WKB -> GeoJSON é a de verdade. */
class CamadaServiceTest {

    private static final byte[] QUADRADO = new WKBWriter().write(new GeometryFactory().createPolygon(new Coordinate[]{
            new Coordinate(-50.0, -25.0), new Coordinate(-49.9, -25.0), new Coordinate(-49.9, -24.9),
            new Coordinate(-50.0, -24.9), new Coordinate(-50.0, -25.0)}));

    private CamadaRepository repositorio;
    private CamadaServiceImpl service;

    @BeforeEach
    void preparar() {
        repositorio = mock(CamadaRepository.class);
        service = new CamadaServiceImpl(repositorio);
    }

    @Test
    void listaReservaLegalEFontesDoInsumo() {
        when(repositorio.temReservaLegal()).thenReturn(true);
        when(repositorio.fontesDoInsumo()).thenReturn(List.of(new FonteInsumo(12L, "APP", "APP do CAR")));

        assertThat(service.listar()).containsExactly(
                new CamadaResponse("reserva-legal", "Reserva Legal (CAR)", "RESERVA_LEGAL", null),
                new CamadaResponse("fonte-12", "APP do CAR", "INSUMO", "APP"));
    }

    @Test
    void semReservaLegalCarregadaNaoListaACamada() {
        when(repositorio.temReservaLegal()).thenReturn(false);
        when(repositorio.fontesDoInsumo()).thenReturn(List.of());

        assertThat(service.listar()).isEmpty();
    }

    @Test
    void reservaLegalViraGeoJsonComOsAtributos() {
        when(repositorio.reservaLegalNaArea(-50.5, -25.5, -49.5, -24.5, 1001)).thenReturn(
                List.of(new ReservaNoMapa("PR-4100103-ABC", new BigDecimal("12.5"), "AT", QUADRADO)));

        MapaImoveisResponse mapa = service.mapa("reserva-legal", -50.5, -25.5, -49.5, -24.5, 1000);

        assertThat(mapa.type()).isEqualTo("FeatureCollection");
        assertThat(mapa.truncado()).isFalse();
        assertThat(mapa.features()).hasSize(1);
        assertThat(mapa.features().get(0).properties())
                .containsEntry("codImovel", "PR-4100103-ABC").containsEntry("situacao", "AT");
        assertThat(mapa.features().get(0).geometry()).contains("\"Polygon\"");
    }

    @Test
    void insumoBuscaPelaSiglaDaFonte() {
        when(repositorio.fonteDoInsumo(12L)).thenReturn(Optional.of(new FonteInsumo(12L, "APP", "APP do CAR")));
        when(repositorio.insumoNaArea(eq("APP"), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(3)))
                .thenReturn(List.of(new InsumoNoMapa("APP", null, null, QUADRADO),
                        new InsumoNoMapa("APP", null, null, QUADRADO),
                        new InsumoNoMapa("APP", null, null, QUADRADO)));

        MapaImoveisResponse mapa = service.mapa("fonte-12", -50.5, -25.5, -49.5, -24.5, 2);

        // Veio um a mais que o limite: corta e avisa para aproximar o zoom
        assertThat(mapa.features()).hasSize(2);
        assertThat(mapa.truncado()).isTrue();
        assertThat(mapa.features().get(0).properties()).containsEntry("categoria", "APP");
    }

    @Test
    void camadaDesconhecidaOuDeFonteSemCargaE404() {
        when(repositorio.fonteDoInsumo(99L)).thenReturn(Optional.empty());

        for (String id : List.of("fonte-99", "fonte-abc", "imoveis", "")) {
            assertThatThrownBy(() -> service.mapa(id, -50.5, -25.5, -49.5, -24.5, 10))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
        verify(repositorio, never()).insumoNaArea(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void areaGrandeDemaisOuIncompletaE400SemIrAoBanco() {
        when(repositorio.reservaLegalNaArea(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.mapa("reserva-legal", -54.0, -26.0, -48.0, -22.0, 10))
                .isInstanceOf(RequisicaoInvalidaException.class).hasMessageContaining("aproxime o zoom");
        assertThatThrownBy(() -> service.mapa("reserva-legal", null, -26.0, -49.0, -25.0, 10))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> service.mapa("reserva-legal", -50.0, -26.0, -49.0, -25.0, 5000))
                .isInstanceOf(RequisicaoInvalidaException.class);
        verify(repositorio, never()).reservaLegalNaArea(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }
}
