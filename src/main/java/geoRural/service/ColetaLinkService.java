package geoRural.service;

import geoRural.dto.ColetaResponse;

public interface ColetaLinkService {

    /**
     * Dispara a coleta do link da fonte no Airflow. A DAG coletar_fonte baixa o link para a zona bruta
     * e manda processar o arquivo; daí em diante ele segue o mesmo caminho de um upload.
     */
    ColetaResponse executar(Long fonteId);
}
