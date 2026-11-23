package geoRural.service;

import java.util.List;

import geoRural.dto.IndicadorMunicipalResponse;

public interface IndicadorMunicipalService {

    List<IndicadorMunicipalResponse> listarIrlMunicipal(
            String estado,
            String codIbge
    );
}