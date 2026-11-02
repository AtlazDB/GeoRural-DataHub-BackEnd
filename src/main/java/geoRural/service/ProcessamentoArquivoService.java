package geoRural.service;

import geoRural.dto.ProcessamentoResponse;

public interface ProcessamentoArquivoService {

    /** Dispara o Airflow para um arquivo da zona bruta, que o processa pelo mapeamento da fonte. */
    ProcessamentoResponse processar(Long arquivoId);

}
