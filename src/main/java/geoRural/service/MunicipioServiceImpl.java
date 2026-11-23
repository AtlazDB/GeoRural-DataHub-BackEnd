package geoRural.service;

import geoRural.entity.Municipio;
import geoRural.exception.MunicipioNaoEncontradoException;
import geoRural.acesso.AcessoTerritorial;
import geoRural.repository.MunicipioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MunicipioServiceImpl implements MunicipioService {

    private final MunicipioRepository repository;
    private final AcessoTerritorial acesso;

    public MunicipioServiceImpl(MunicipioRepository repository, AcessoTerritorial acesso) {
        this.repository = repository;
        this.acesso = acesso;
    }

    @Override
    public List<Municipio> listar(String estado) {
        List<Municipio> lista = estado == null || estado.isBlank()
                ? repository.findAllByOrderByEstadoAscNomeAsc()
                : repository.findByEstadoOrderByNome(estado.trim().toUpperCase());
        return acesso.municipiosLiberados()
                .map(liberados -> lista.stream().filter(m -> liberados.contains(m.getCodIbge())).toList())
                .orElse(lista);
    }

    @Override
    public Municipio buscarPorCodIbge(String codIbge) {
        if (!acesso.permiteMunicipio(codIbge)) {
            throw new MunicipioNaoEncontradoException(codIbge);
        }
        return repository.findByCodIbge(codIbge)
                .orElseThrow(() -> new MunicipioNaoEncontradoException(codIbge));
    }

}
