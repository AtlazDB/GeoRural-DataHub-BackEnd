package geoRural.service;

import geoRural.dto.ColetaResponse;
import geoRural.entity.Fonte;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * O "Executar agora" de uma fonte por link só registra o pedido na fonte (coleta_solicitada_em).
 * A DAG coleta_fontes procura os pedidos a cada 2 minutos, pagina o serviço (ArcGIS, WFS) e grava
 * o zip na zona bruta como RECEBIDO; daí em diante é igual a um upload. O andamento aparece na
 * fonte (coletando, ultimaColetaMensagem, ultimaColetaEm), sem segurar a requisição da tela.
 */
@Service
public class ColetaLinkServiceImpl implements ColetaLinkService {

    private static final Logger log = LoggerFactory.getLogger(ColetaLinkServiceImpl.class);

    private final FonteService fonteService;
    private final FonteRepository fonteRepository;

    public ColetaLinkServiceImpl(FonteService fonteService, FonteRepository fonteRepository) {
        this.fonteService = fonteService;
        this.fonteRepository = fonteRepository;
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
        log.info("Fonte {}: coleta pedida ao Airflow", fonteId);
        return ColetaResponse.solicitada();
    }
}
