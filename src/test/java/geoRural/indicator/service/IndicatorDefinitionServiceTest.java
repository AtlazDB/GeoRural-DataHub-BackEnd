package geoRural.indicator.service;

import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import geoRural.indicator.exception.DuplicateIndicatorCodeException;
import geoRural.indicator.repository.IndicatorDefinitionRepository;
import geoRural.repository.FonteRepository;
import geoRural.service.AirflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O cadastro de indicador, sem banco: só as regras do IndicatorDefinitionServiceImpl. */
class IndicatorDefinitionServiceTest {

    private static final long SOURCE = 7L;

    private final IndicatorDefinitionRepository repository = mock(IndicatorDefinitionRepository.class);
    private final FonteRepository fonteRepository = mock(FonteRepository.class);
    private final AirflowClient airflow = mock(AirflowClient.class);
    private final IndicatorDefinitionService service =
            new IndicatorDefinitionServiceImpl(repository, fonteRepository, airflow);

    @BeforeEach
    void sourceWithPoints() {
        when(fonteRepository.existsById(SOURCE)).thenReturn(true);
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(1, 5));
        when(repository.findByCode("DFOCO")).thenReturn(Optional.of(saved("DFOCO")));
    }

    @Test
    void createsValidIndicatorAndReturnsWhatWasSaved() {
        IndicatorDefinitionResponse response = service.create(request("DFOCO", "POINT_DENSITY", validRanges()));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
        assertThat(response.code()).isEqualTo("DFOCO");
        assertThat(response.active()).isTrue();
    }

    // ---- sigla

    @Test
    void rejectsCodeOutOfFormat() {
        for (String code : List.of("dfoco", "1FOCO", "D", "D-FOCO", "DFOCO123456")) {
            assertThatThrownBy(() -> service.create(request(code, "POINT_DENSITY", validRanges())))
                    .as(code)
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasMessageContaining("sigla");
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsDuplicateCode() {
        when(repository.existsByCode("DFOCO")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("DFOCO", "POINT_DENSITY", validRanges())))
                .isInstanceOf(DuplicateIndicatorCodeException.class);
        verify(repository, never()).insert(any());
    }

    // ---- modelo e fonte

    @Test
    void rejectsUnknownCalculationModel() {
        assertThatThrownBy(() -> service.create(request("DFOCO", "FREE_FORMULA", validRanges())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("Modelo de cálculo");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsSourceThatDoesNotExist() {
        when(fonteRepository.existsById(SOURCE)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("DFOCO", "POINT_DENSITY", validRanges())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("não existe");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsSourceWithoutDataInInputFeature() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request("DFOCO", "POINT_DENSITY", validRanges())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("ainda não tem dados");
        verify(repository, never()).insert(any());
    }

    @Test
    void pointDensityRejectsSourceWithPolygons() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(1, 3));

        assertThatThrownBy(() -> service.create(request("DFOCO", "POINT_DENSITY", validRanges())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("só com pontos");
        verify(repository, never()).insert(any());
    }

    @Test
    void areaCoverageRejectsSourceWithPoints() {
        assertThatThrownBy(() -> service.create(request("DFOCO", "AREA_COVERAGE", validRanges())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("só com polígonos");
        verify(repository, never()).insert(any());
    }

    @Test
    void areaCoverageAcceptsPolygonsAndMultipolygons() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(3, 7));

        service.create(request("DFOCO", "AREA_COVERAGE", validRanges()));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
    }

    // ---- faixas

    @Test
    void acceptsSingleRangeWithoutLimit() {
        service.create(request("DFOCO", "POINT_DENSITY", List.of(new StatusRangeRequest("VALIDACAO", null, null))));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
    }

    @Test
    void rejectsUnknownStatus() {
        List<StatusRangeRequest> ranges = List.of(
                range("BOM", "10", true),
                new StatusRangeRequest("CRITICO", null, null));

        assertRangeError(ranges, "Faixa 1: o status");
    }

    @Test
    void rejectsLastRangeWithLimit() {
        List<StatusRangeRequest> ranges = List.of(
                range("CONFORME", "10", true),
                range("CRITICO", "20", true));

        assertRangeError(ranges, "Faixa 2: a última faixa não tem limite");
    }

    @Test
    void rejectsRangeWithoutLimitBeforeTheLast() {
        List<StatusRangeRequest> ranges = List.of(
                new StatusRangeRequest("CONFORME", null, null),
                new StatusRangeRequest("CRITICO", null, null));

        assertRangeError(ranges, "Faixa 1: só a última faixa");
    }

    @Test
    void rejectsRangeWithoutIncludeLimitBeforeTheLast() {
        List<StatusRangeRequest> ranges = List.of(
                range("CONFORME", "10", null),
                new StatusRangeRequest("CRITICO", null, null));

        assertRangeError(ranges, "Faixa 1: só a última faixa");
    }

    @Test
    void rejectsLimitsOutOfOrder() {
        List<StatusRangeRequest> ranges = List.of(
                range("CONFORME", "20", true),
                range("ALERTA", "10", false),
                new StatusRangeRequest("CRITICO", null, null));

        assertRangeError(ranges, "Faixa 2: o limite tem que ser maior");
    }

    @Test
    void rejectsRepeatedLimitEvenWithDifferentScale() {
        // 10 e 10.0: o compareTo trata como iguais (o equals do BigDecimal não)
        List<StatusRangeRequest> ranges = List.of(
                range("CONFORME", "10", true),
                range("ALERTA", "10.0", false),
                new StatusRangeRequest("CRITICO", null, null));

        assertRangeError(ranges, "Faixa 2: o limite tem que ser maior");
    }

    // ---- consultar e desativar

    @Test
    void findByCodeThrowsNotFoundWhenMissing() {
        when(repository.findByCode("NADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("NADA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatesWithoutDeleting() {
        when(repository.deactivate("DFOCO")).thenReturn(true);

        service.deactivate("DFOCO");

        verify(repository).deactivate("DFOCO");
    }

    @Test
    void deactivateThrowsNotFoundWhenMissing() {
        when(repository.deactivate("NADA")).thenReturn(false);

        assertThatThrownBy(() -> service.deactivate("NADA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- calcular (US13.T06)

    @Test
    void calculateTriggersTheDagWithTheCode() {
        service.calculate("DFOCO");

        verify(airflow).disparar("calcular_indicador", Map.of("code", "DFOCO"));
    }

    @Test
    void calculateMissingThrowsNotFound() {
        when(repository.findByCode("NADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate("NADA"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(airflow, never()).disparar(anyString(), anyMap());
    }

    @Test
    void calculateRefusesDeactivatedIndicator() {
        IndicatorDefinitionResponse ativo = saved("DFOCO");
        when(repository.findByCode("DFOCO")).thenReturn(Optional.of(new IndicatorDefinitionResponse(
                ativo.code(), ativo.name(), ativo.unit(), ativo.description(), ativo.fonteId(),
                ativo.calculationModel(), ativo.parameters(), ativo.ranges(), false, ativo.createdAt())));

        assertThatThrownBy(() -> service.calculate("DFOCO"))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("desativado");
        verify(airflow, never()).disparar(anyString(), anyMap());
    }

    @Test
    void calculateAreaCoverageTriggersTheSameDag() {
        IndicatorDefinitionResponse ativo = saved("DCOB");
        when(repository.findByCode("DCOB")).thenReturn(Optional.of(new IndicatorDefinitionResponse(
                "DCOB", ativo.name(), ativo.unit(), ativo.description(), ativo.fonteId(),
                "AREA_COVERAGE", ativo.parameters(), ativo.ranges(), true, ativo.createdAt())));

        service.calculate("DCOB");

        verify(airflow).disparar("calcular_indicador", Map.of("code", "DCOB"));
    }

    // ---- montagem dos dados de teste

    private void assertRangeError(List<StatusRangeRequest> ranges, String message) {
        assertThatThrownBy(() -> service.create(request("DFOCO", "POINT_DENSITY", ranges)))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining(message)
                .extracting(e -> ((RequisicaoInvalidaException) e).getCampos())
                .isEqualTo(List.of("ranges"));
        verify(repository, never()).insert(any());
    }

    private static IndicatorDefinitionRequest request(String code, String model, List<StatusRangeRequest> ranges) {
        return new IndicatorDefinitionRequest(code, "Densidade de focos", "focos/km²", null,
                SOURCE, model, Map.of("factor", 100), ranges);
    }

    private static List<StatusRangeRequest> validRanges() {
        return List.of(
                range("CONFORME", "10", true),
                range("ALERTA", "20", false),
                new StatusRangeRequest("CRITICO", null, null));
    }

    private static StatusRangeRequest range(String status, String limit, Boolean includeLimit) {
        return new StatusRangeRequest(status, new BigDecimal(limit), includeLimit);
    }

    private static IndicatorDefinitionResponse saved(String code) {
        return new IndicatorDefinitionResponse(code, "Densidade de focos", "focos/km²", null, SOURCE,
                "POINT_DENSITY", Map.of("factor", 100), validRanges(), true, OffsetDateTime.now());
    }
}
