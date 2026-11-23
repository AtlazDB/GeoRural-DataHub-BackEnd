package geoRural.service;

import java.util.List;

import org.springframework.stereotype.Service;

import geoRural.dto.IndicadorMunicipalResponse;
import geoRural.repository.IndicadorMunicipalRepository;

@Service
public class IndicadorMunicipalServiceImpl
        implements IndicadorMunicipalService {

    private final IndicadorMunicipalRepository repository;

    public IndicadorMunicipalServiceImpl(
            IndicadorMunicipalRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<IndicadorMunicipalResponse> listarIrlMunicipal(
            String estado,
            String codIbge) {

        return repository.listarIrlMunicipal(estado, codIbge);
    }
}