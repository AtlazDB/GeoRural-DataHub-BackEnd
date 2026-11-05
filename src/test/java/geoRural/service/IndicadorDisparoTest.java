package geoRural.service;

import geoRural.repository.ImovelRuralRepository;
import geoRural.repository.IndicadorRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** O "Calcular agora" do IRL/IFC, sem banco: só dispara a DAG, não calcula nada no Oracle (US13.T10). */
class IndicadorDisparoTest {

    private final IndicadorRepository repository = mock(IndicadorRepository.class);
    private final AirflowClient airflow = mock(AirflowClient.class);
    private final IndicadorService service =
            new IndicadorServiceImpl(repository, mock(ImovelRuralRepository.class), airflow);

    @Test
    void calcularDisparaADagDoAirflow() {
        service.calcular();

        verify(airflow).disparar("calcular_indicadores", Map.of());
        verifyNoInteractions(repository);
    }
}
