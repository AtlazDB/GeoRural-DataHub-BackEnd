package geoRural.indicator.service;

import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;

import java.util.List;

public interface IndicatorDefinitionService {
    IndicatorDefinitionResponse create(IndicatorDefinitionRequest request);

    List<IndicatorDefinitionResponse> findAll();

    IndicatorDefinitionResponse findByCode(String code);

    void deactivate(String code);

    /** Dispara a DAG calcular_indicador no Airflow e volta logo (o cálculo leva minutos). */
    void calculate(String code);
}
