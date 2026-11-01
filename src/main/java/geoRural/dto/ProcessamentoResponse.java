package geoRural.dto;

/**
 * Resultado de um pedido de processamento: o arquivo voltou para a fila do Airflow (RECEBIDO).
 * O resultado final (PROCESSADO, REJEITADO, AGUARDANDO_MAPEAMENTO) chega depois, pelo status do
 * arquivo.
 */
public record ProcessamentoResponse(Long arquivoId, String status) {

    public static ProcessamentoResponse agendado(long arquivoId) {
        return new ProcessamentoResponse(arquivoId, "RECEBIDO");
    }
}
