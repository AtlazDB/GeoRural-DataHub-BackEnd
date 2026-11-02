package geoRural.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import geoRural.dto.ProcessamentoResponse;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.ArquivoNaoEncontradoException;
import geoRural.exception.ProcessamentoEmAndamentoException;

/**
 * Quem processa é o Airflow: aqui o arquivo, de qualquer fonte, é reservado (PROCESSANDO) e a DAG
 * processar_arquivo é disparada na hora. Ela escolhe o caminho: o shapefile do CAR pelo caminho
 * próprio, o resto pelo genérico, guiado pelo mapeamento da fonte. O resultado chega depois, pelo
 * status do arquivo.
 */
@Service
public class ProcessamentoArquivoServiceImpl implements ProcessamentoArquivoService {

    private static final String DAG_PROCESSAR = "processar_arquivo";

    private final JdbcTemplate jdbc;
    private final AirflowClient airflow;

    public ProcessamentoArquivoServiceImpl(JdbcTemplate jdbc, AirflowClient airflow) {
        this.jdbc = jdbc;
        this.airflow = airflow;
    }

    @Override
    public ProcessamentoResponse processar(Long arquivoId) {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM arquivo_bruto WHERE id = ?", Integer.class, arquivoId);
        if (existe == null || existe == 0) {
            throw new ArquivoNaoEncontradoException(arquivoId);
        }
        // Vale também para reprocessar um PROCESSADO ou REJEITADO. O UPDATE condicional recusa o
        // arquivo que a DAG está processando agora, e dois cliques não disparam duas execuções.
        int reservados = jdbc.update("""
                UPDATE arquivo_bruto SET status = 'PROCESSANDO', processado_em = NULL
                 WHERE id = ? AND status <> 'PROCESSANDO'
                """, arquivoId);
        if (reservados == 0) {
            throw new ProcessamentoEmAndamentoException(arquivoId);
        }
        try {
            airflow.disparar(DAG_PROCESSAR, Map.of("arquivo_bruto_id", arquivoId, "reservado", true));
        } catch (AirflowIndisponivelException e) {
            // Sem disparo, o arquivo não pode ficar preso em PROCESSANDO: volta, e o botão tenta de novo.
            jdbc.update("UPDATE arquivo_bruto SET status = 'RECEBIDO' WHERE id = ?", arquivoId);
            throw e;
        }
        return ProcessamentoResponse.disparado(arquivoId);
    }
}
