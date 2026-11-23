package geoRural.service;

import geoRural.dto.ProcessamentoResponse;

public interface ProcessamentoArquivoService {

    /** Dispara o Airflow para um arquivo da zona bruta, que o processa pelo mapeamento da fonte. */
    ProcessamentoResponse processar(Long arquivoId);

    /** Encerra no Airflow o processamento em andamento; o arquivo fica REJEITADO e pode ser processado de novo. */
    ProcessamentoResponse cancelar(Long arquivoId);

}
