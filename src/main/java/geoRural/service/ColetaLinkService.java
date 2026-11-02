package geoRural.service;

import geoRural.dto.ColetaResponse;

public interface ColetaLinkService {

    /**
     * Pede ao Airflow a coleta do link da fonte. A DAG coleta_fontes baixa o link para a zona bruta
     * e registra o arquivo como RECEBIDO; daí em diante ele segue o mesmo caminho de um upload.
     */
    ColetaResponse executar(Long fonteId);
}
