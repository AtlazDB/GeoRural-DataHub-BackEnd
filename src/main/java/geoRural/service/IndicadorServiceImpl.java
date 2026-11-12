package geoRural.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import geoRural.acesso.AcessoTerritorial;
import geoRural.dto.IndicadorResponse;
import geoRural.dto.IndicadoresImovelResponse;
import geoRural.exception.ImovelNaoEncontradoException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.ImovelRuralRepository;
import geoRural.repository.IndicadorRepository;

@Service
public class IndicadorServiceImpl implements IndicadorService {

    static final List<String> INDICADORES_CALCULADOS = List.of("IRL", "IFC");
    // O cálculo é do Airflow (GeoPandas): dags/calcular_indicadores.py (o pkg_indicadores saiu na V24).
    static final String DAG_CALCULAR = "calcular_indicadores";

    private final IndicadorRepository repository;
    private final ImovelRuralRepository imovelRepository;
    private final AirflowClient airflow;
    private final AcessoTerritorial acesso;

    public IndicadorServiceImpl(IndicadorRepository repository, ImovelRuralRepository imovelRepository,
                                AirflowClient airflow, AcessoTerritorial acesso) {
        this.repository = repository;
        this.imovelRepository = imovelRepository;
        this.airflow = airflow;
        this.acesso = acesso;
    }

    @Override
    public void calcular() {
        airflow.disparar(DAG_CALCULAR, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresImovelResponse buscarPorImovel(String codImovel) {
        exigirImovel(codImovel);
        return new IndicadoresImovelResponse(codImovel, repository.buscarAtuais(codImovel));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndicadorResponse> historico(String codImovel, String indicador) {
        String sigla = indicador == null ? "" : indicador.trim().toUpperCase(Locale.ROOT);
        if (!INDICADORES_CALCULADOS.contains(sigla)) {
            throw new RequisicaoInvalidaException(
                    "Indicador inválido. Use: " + String.join(", ", INDICADORES_CALCULADOS) + ".",
                    List.of("indicador"));
        }
        exigirImovel(codImovel);
        return repository.buscarHistorico(codImovel, sigla);
    }

    private void exigirImovel(String codImovel) {
        acesso.exigirImovel(codImovel);
        if (!imovelRepository.existsByCodImovel(codImovel)) {
            throw new ImovelNaoEncontradoException(codImovel);
        }
    }
}
