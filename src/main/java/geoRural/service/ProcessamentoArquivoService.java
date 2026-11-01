package geoRural.service;

import geoRural.dto.ProcessamentoResponse;

public interface ProcessamentoArquivoService {

    /** Devolve um arquivo da zona bruta para a fila do Airflow, que o processa pelo mapeamento da fonte. */
    ProcessamentoResponse processar(Long arquivoId);

}
