package geoRural.service;

import geoRural.dto.ColetaResponse;
import geoRural.entity.Fonte;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * O "Executar agora" de uma fonte por link registra o pedido na fonte (coleta_solicitada_em) e
 * dispara a DAG coletar_fonte, que pagina o serviço (ArcGIS, WFS), grava o zip na zona bruta e manda
 * processar; daí em diante é igual a um upload. O andamento aparece na fonte (coletando,
 * ultimaColetaMensagem, ultimaColetaEm), sem segurar a requisição da tela.
 */
@Service
public class ColetaLinkServiceImpl implements ColetaLinkService {

    private static final Logger log = LoggerFactory.getLogger(ColetaLinkServiceImpl.class);

    private static final String DAG_COLETAR = "coletar_fonte";

    private final FonteService fonteService;
    private final FonteRepository fonteRepository;
    private final AirflowClient airflow;

    public ColetaLinkServiceImpl(FonteService fonteService, FonteRepository fonteRepository, AirflowClient airflow) {
        this.fonteService = fonteService;
        this.fonteRepository = fonteRepository;
        this.airflow = airflow;
    }

    @Override
    public ColetaResponse executar(Long fonteId) {
        Fonte fonte = fonteService.buscarPorId(fonteId);
        if (fonte.getUrlOrigem() == null || fonte.getUrlOrigem().isBlank()) {
            throw new RequisicaoInvalidaException(
                    "Esta fonte não tem link de origem: cadastre a URL para poder coletar.", List.of("urlOrigem"));
        }
        fonte.setColetaSolicitadaEm(OffsetDateTime.now());
        fonteRepository.save(fonte);
        try {
            airflow.disparar(DAG_COLETAR, Map.of("fonte_id", fonteId, "pedido", true));
        } catch (AirflowIndisponivelException e) {
            // Sem disparo, a fonte não pode ficar "coletando": o pedido sai, e o botão tenta de novo.
            fonte.setColetaSolicitadaEm(null);
            fonteRepository.save(fonte);
            throw e;
        }
        log.info("Fonte {}: coleta disparada no Airflow", fonteId);
        return ColetaResponse.solicitada();
    }
}
