package geoRural.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import geoRural.dto.ProcessamentoResponse;
import geoRural.exception.ArquivoNaoEncontradoException;
import geoRural.exception.ProcessamentoEmAndamentoException;

/**
 * Quem processa é o Airflow: aqui o arquivo, de qualquer fonte, só volta para a fila (RECEBIDO).
 * O varredor_bruta pega em até 2 minutos e a DAG processar_arquivo escolhe o caminho: o shapefile
 * do CAR pelo caminho próprio, o resto pelo genérico, guiado pelo mapeamento da fonte. O resultado
 * chega depois, pelo status do arquivo.
 */
@Service
public class ProcessamentoArquivoServiceImpl implements ProcessamentoArquivoService {

    private final JdbcTemplate jdbc;

    public ProcessamentoArquivoServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProcessamentoResponse processar(Long arquivoId) {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM arquivo_bruto WHERE id = ?", Integer.class, arquivoId);
        if (existe == null || existe == 0) {
            throw new ArquivoNaoEncontradoException(arquivoId);
        }
        // Vale também para reprocessar um PROCESSADO ou REJEITADO. O UPDATE condicional recusa o
        // arquivo que a DAG está processando agora.
        int marcados = jdbc.update("""
                UPDATE arquivo_bruto SET status = 'RECEBIDO', processado_em = NULL
                 WHERE id = ? AND status <> 'PROCESSANDO'
                """, arquivoId);
        if (marcados == 0) {
            throw new ProcessamentoEmAndamentoException(arquivoId);
        }
        return ProcessamentoResponse.agendado(arquivoId);
    }
}
