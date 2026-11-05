package geoRural.service;

import geoRural.dto.IndicadorResponse;
import geoRural.dto.IndicadoresImovelResponse;

import java.util.List;

public interface IndicadorService {

    /**
     * Dispara o recálculo do IRL e do IFC de todos os imóveis (DAG calcular_indicadores no Airflow)
     * e volta logo: o cálculo leva alguns minutos, e os valores novos aparecem quando ele termina.
     */
    void calcular();

    IndicadoresImovelResponse buscarPorImovel(String codImovel);

    List<IndicadorResponse> historico(String codImovel, String indicador);

}
