package geoRural.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import geoRural.dto.ProcessamentoResponse;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.exception.ArquivoNaoEncontradoException;
import geoRural.exception.ProcessamentoEmAndamentoException;
import geoRural.exception.ProcessamentoNaoEmAndamentoException;

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

    /**
     * Encerra a execução no Airflow primeiro e só depois marca o arquivo: se o Airflow estiver fora
     * do ar (503), o arquivo continua PROCESSANDO, coerente com o que de fato está rodando.
     * Os blocos que a DAG já confirmou ficam gravados, como em qualquer falha no meio; processar
     * de novo refaz a carga inteira pelo MERGE.
     */
    @Override
    public ProcessamentoResponse cancelar(Long arquivoId) {
        String status = jdbc.query("SELECT status FROM arquivo_bruto WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, arquivoId);
        if (status == null) {
            throw new ArquivoNaoEncontradoException(arquivoId);
        }
        if (!"PROCESSANDO".equals(status)) {
            throw new ProcessamentoNaoEmAndamentoException(arquivoId);
        }
        // Mesmo sem execução viva no Airflow (arquivo preso em PROCESSANDO), o arquivo é liberado.
        airflow.cancelar(DAG_PROCESSAR, "arquivo_bruto_id", arquivoId);

        jdbc.update("""
                UPDATE execucao SET status = 'FALHOU', finalizada_em = SYSTIMESTAMP,
                       mensagem_erro = 'Cancelado pelo usuário.'
                 WHERE arquivo_bruto_id = ? AND status = 'EM_ANDAMENTO'
                """, arquivoId);
        jdbc.update("""
                UPDATE arquivo_bruto SET status = 'REJEITADO', processado_em = SYSTIMESTAMP
                 WHERE id = ? AND status = 'PROCESSANDO'
                """, arquivoId);
        return ProcessamentoResponse.cancelado(arquivoId);
    }
}
