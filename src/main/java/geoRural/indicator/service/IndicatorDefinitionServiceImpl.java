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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class IndicatorDefinitionServiceImpl implements  IndicatorDefinitionService {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");
    private static final String POINT_DENSITY = "POINT_DENSITY";
    private static final String AREA_COVERAGE = "AREA_COVERAGE";

    // get_gtype(): 1 = ponto, 5 = multiponto, 3 = polígono, 7 = multipolígono
    private static final Set<Integer> POINTS = Set.of(1, 5);
    private static final Set<Integer> POLYGONS = Set.of(3, 7);
    private static final Set<String> STATUSES = Set.of("CONFORME", "ALERTA", "CRITICO", "VALIDACAO");
    // O cálculo é do Airflow (GeoPandas): dags/calcular_indicador.py
    private static final String DAG_CALCULATE = "calcular_indicador";

    private final IndicatorDefinitionRepository repository;
    private final FonteRepository fonteRepository;
    private final AirflowClient airflow;

    public IndicatorDefinitionServiceImpl(IndicatorDefinitionRepository repository,
                                          FonteRepository fonteRepository,
                                          AirflowClient airflow) {
        this.repository = repository;
        this.fonteRepository = fonteRepository;
        this.airflow = airflow;
    }

    @Override
    @Transactional
    public IndicatorDefinitionResponse create(IndicatorDefinitionRequest request) {
        validateCode(request.code());
        validateRanges(request.ranges());
        validateSource(request.fonteId(), request.calculationModel());
        repository.insert(request);
        return findByCode(request.code());
    }

    @Override
    public List<IndicatorDefinitionResponse> findAll() {
        return repository.findAll();
    }

    @Override
    public IndicatorDefinitionResponse findByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Indicador \"" + code + "\" não encontrado."));
    }

    @Override
    @Transactional
    public void deactivate(String code) {
        if (!repository.deactivate(code)) {
            throw new ResourceNotFoundException("Indicador \"" + code + "\" não encontrado.");
        }
    }

    @Override
    public void calculate(String code) {
        IndicatorDefinitionResponse indicator = findByCode(code);
        if (!indicator.active()) {
            throw new RequisicaoInvalidaException("O indicador \"" + code + "\" está desativado e não é mais calculado.");
        }
        // Os dois modelos (POINT_DENSITY e AREA_COVERAGE) são calculados pela mesma DAG.
        airflow.disparar(DAG_CALCULATE, Map.of("code", code));
    }

    private void validateCode(String code) {
        if (!CODE.matcher(code).matches()) {
            throw new RequisicaoInvalidaException(
                    "A sigla deve começar com letra e ter de 2 a 10 letras maiúsculas ou números.",
                    List.of("code"));
        }
        if (repository.existsByCode(code)) {
            throw new DuplicateIndicatorCodeException(code);
        }
    }

    private void validateSource(Long fonteId, String calculationModel) {
        Set<Integer> expected = switch (calculationModel) {
            case POINT_DENSITY -> POINTS;
            case AREA_COVERAGE -> POLYGONS;
            default -> throw new RequisicaoInvalidaException(
                    "Modelo de cálculo deve ser POINT_DENSITY ou AREA_COVERAGE.", List.of("calculationModel"));
        };
        if (!fonteRepository.existsById(fonteId)) {
            throw new RequisicaoInvalidaException("A fonte " + fonteId + " não existe.", List.of("fonteId"));
        }
        List<Integer> types = repository.findGeometryTypesBySource(fonteId);
        if (types.isEmpty()) {
            throw new RequisicaoInvalidaException(
                    "A fonte " + fonteId + " ainda não tem dados processados no insumo genérico.", List.of("fonteId"));
        }
        if (!expected.containsAll(types)) {
            throw new RequisicaoInvalidaException(
                    calculationModel.equals(POINT_DENSITY)
                            ? "Densidade de pontos exige uma fonte só com pontos."
                            : "Cobertura de área exige uma fonte só com polígonos.",
                    List.of("calculationModel"));
        }
    }

     // Faixas em ordem crescente de limite; só a última fica sem limite (vale para tudo acima
     // da anterior). Ex.: [{CONFORME, 10, true}, {ALERTA, 20, false}, {CRITICO}].

    private void validateRanges(List<StatusRangeRequest> ranges) {
        BigDecimal previous = null;
        for (int i = 0; i < ranges.size(); i++) {
            StatusRangeRequest range = ranges.get(i);
            boolean last = i == ranges.size() - 1;
            if (!STATUSES.contains(range.status())) {
                throw invalidRange(i, "o status deve ser CONFORME, ALERTA, CRITICO ou VALIDACAO.");
            }
            if (last) {
                if (range.limit() != null) {
                    throw invalidRange(i, "a última faixa não tem limite (vale para tudo acima da anterior).");
                }
            } else {
                if (range.limit() == null || range.includeLimit() == null) {
                    throw invalidRange(i, "só a última faixa pode ficar sem limit e includeLimit.");
                }
                if (previous != null && range.limit().compareTo(previous) <= 0) {
                    throw invalidRange(i, "o limite tem que ser maior que o da faixa anterior.");
                }
                previous = range.limit();
            }
        }
    }

    private RequisicaoInvalidaException invalidRange(int index, String message) {
        return new RequisicaoInvalidaException("Faixa " + (index + 1) + ": " + message, List.of("ranges"));
    }
}

