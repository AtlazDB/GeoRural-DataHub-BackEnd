package geoRural.indicator.service;

import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.dto.StatusRangeRequest;
import geoRural.indicator.exception.DuplicateIndicatorCodeException;
import geoRural.indicator.dto.IndicatorMunicipalityResponse;
import geoRural.indicator.dto.IndicatorResultResponse;
import geoRural.indicator.repository.IndicatorDefinitionRepository;
import geoRural.indicator.repository.IndicatorResultRepository;
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
    private final IndicatorResultRepository resultRepository = mock(IndicatorResultRepository.class);
    private final FonteRepository fonteRepository = mock(FonteRepository.class);
    private final AirflowClient airflow = mock(AirflowClient.class);
    private final IndicatorDefinitionService service =
            new IndicatorDefinitionServiceImpl(repository, resultRepository, fonteRepository, airflow);

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

    // ---- filtro por data (parameters.since, IDesmat)

    @Test
    void acceptsSinceWhenTheSourceHasReferenceDates() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(3, 7));
        when(repository.hasReferenceDateBySource(SOURCE)).thenReturn(true);
        when(repository.findByCode("DESM08")).thenReturn(Optional.of(saved("DESM08")));

        service.create(withParameters("AREA_COVERAGE", Map.of("since", "2008-07-22")));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
    }

    @Test
    void rejectsSinceOutOfFormat() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(3, 7));
        when(repository.hasReferenceDateBySource(SOURCE)).thenReturn(true);

        for (String since : List.of("22/07/2008", "2008-13-01", "ontem")) {
            assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("since", since))))
                    .as(since)
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasMessageContaining("AAAA-MM-DD");
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsSinceWhenTheSourceHasNoReferenceDates() {
        when(repository.findGeometryTypesBySource(SOURCE)).thenReturn(List.of(3, 7));
        when(repository.hasReferenceDateBySource(SOURCE)).thenReturn(false);

        assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("since", "2008-07-22"))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("data de referência");
        verify(repository, never()).insert(any());
    }

    @Test
    void withoutSinceDoesNotLookForReferenceDates() {
        service.create(request("DFOCO", "POINT_DENSITY", validRanges()));

        verify(repository, never()).hasReferenceDateBySource(any());
    }

    // ---- várias fontes (parameters.extraSources, ISAP)

    private void polygonSource(long id) {
        when(fonteRepository.existsById(id)).thenReturn(true);
        when(repository.findGeometryTypesBySource(id)).thenReturn(List.of(3, 7));
    }

    @Test
    void acceptsExtraSourcesWhenAllAreValid() {
        polygonSource(SOURCE);
        polygonSource(12L);
        polygonSource(15L);
        when(repository.findByCode("DESM08")).thenReturn(Optional.of(saved("DESM08")));

        service.create(withParameters("AREA_COVERAGE", Map.of("extraSources", List.of(12, 15))));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
        verify(repository).findGeometryTypesBySource(12L);
        verify(repository).findGeometryTypesBySource(15L);
    }

    @Test
    void rejectsExtraSourceThatDoesNotExist() {
        polygonSource(SOURCE);
        when(fonteRepository.existsById(12L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("extraSources", List.of(12)))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("fonte 12 não existe");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsExtraSourceWithTheWrongGeometry() {
        polygonSource(SOURCE);
        when(fonteRepository.existsById(12L)).thenReturn(true);
        when(repository.findGeometryTypesBySource(12L)).thenReturn(List.of(1));

        assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("extraSources", List.of(12)))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("a fonte 12 tem outros tipos");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsExtraSourcesThatAreNotAListOfIds() {
        polygonSource(SOURCE);

        for (Object extras : List.of("12,15", List.of(12, "UC"))) {
            assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("extraSources", extras))))
                    .as(extras.toString())
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasMessageContaining("extraSources");
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void sinceIsCheckedInEverySource() {
        polygonSource(SOURCE);
        polygonSource(12L);
        when(repository.hasReferenceDateBySource(SOURCE)).thenReturn(true);
        when(repository.hasReferenceDateBySource(12L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE",
                Map.of("since", "2008-07-22", "extraSources", List.of(12)))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("fonte 12");
        verify(repository, never()).insert(any());
    }

    // ---- cobertura dentro de uma camada base (BASE_COVERAGE, IAPP)

    @Test
    void baseCoverageAcceptsPolygonCoverAndBase() {
        polygonSource(SOURCE);
        polygonSource(20L);
        when(repository.findByCode("DESM08")).thenReturn(Optional.of(saved("DESM08")));

        service.create(withParameters("BASE_COVERAGE", Map.of("baseSources", List.of(20))));

        verify(repository).insert(any(IndicatorDefinitionRequest.class));
        verify(repository).findGeometryTypesBySource(20L);
    }

    @Test
    void baseCoverageRequiresTheBaseLayer() {
        polygonSource(SOURCE);

        assertThatThrownBy(() -> service.create(withParameters("BASE_COVERAGE", Map.of())))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("baseSources");
        verify(repository, never()).insert(any());
    }

    @Test
    void baseCoverageRejectsBaseWithPoints() {
        polygonSource(SOURCE);
        when(fonteRepository.existsById(20L)).thenReturn(true);
        when(repository.findGeometryTypesBySource(20L)).thenReturn(List.of(1));

        assertThatThrownBy(() -> service.create(withParameters("BASE_COVERAGE", Map.of("baseSources", List.of(20)))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("a fonte 20 tem outros tipos");
        verify(repository, never()).insert(any());
    }

    @Test
    void baseSourcesIsRefusedInOtherModels() {
        polygonSource(SOURCE);

        assertThatThrownBy(() -> service.create(withParameters("AREA_COVERAGE", Map.of("baseSources", List.of(20)))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("só vale para o modelo BASE_COVERAGE");
        verify(repository, never()).insert(any());
    }

    // ---- agregação por município (D4)

    @Test
    void findByMunicipalityReturnsWhatTheRepositoryAggregated() {
        IndicatorMunicipalityResponse curitiba = new IndicatorMunicipalityResponse("4106902", "Curitiba", "PR",
                3, 1, 1, 1, 0, new BigDecimal("12.5000"), new BigDecimal("7"));
        when(resultRepository.findByMunicipality("DFOCO")).thenReturn(List.of(curitiba));

        assertThat(service.findByMunicipality("DFOCO")).containsExactly(curitiba);
    }

    @Test
    void findByMunicipalityOfMissingIndicatorThrowsNotFound() {
        when(repository.findByCode("NADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByMunicipality("NADA"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(resultRepository, never()).findByMunicipality(anyString());
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

    // ---- resultado (US13.T08)

    @Test
    void findLatestResultReturnsWhatTheRepositoryFound() {
        IndicatorResultResponse result = new IndicatorResultResponse("DFOCO", "SP-1", new BigDecimal("19.9005"),
                "ALERTA", new BigDecimal("2"), null, OffsetDateTime.parse("2026-11-05T12:00:00Z"));
        when(resultRepository.findLatest("DFOCO", "SP-1")).thenReturn(Optional.of(result));

        assertThat(service.findLatestResult("DFOCO", "SP-1")).isEqualTo(result);
    }

    @Test
    void findLatestResultOfMissingIndicatorThrowsNotFoundWithoutReadingResults() {
        when(repository.findByCode("NADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLatestResult("NADA", "SP-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("não encontrado");
        verify(resultRepository, never()).findLatest(anyString(), anyString());
    }

    @Test
    void findLatestResultNotCalculatedYetThrowsNotFound() {
        when(resultRepository.findLatest("DFOCO", "SP-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLatestResult("DFOCO", "SP-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ainda não foi calculado");
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

    private static IndicatorDefinitionRequest withParameters(String model, Map<String, Object> parameters) {
        return new IndicatorDefinitionRequest("DESM08", "Desmatamento após 2008", "%", null,
                SOURCE, model, parameters, validRanges());
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
