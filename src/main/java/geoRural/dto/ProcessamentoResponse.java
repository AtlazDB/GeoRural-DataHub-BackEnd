package geoRural.dto;

/**
 * Resultado de um pedido de processamento: o arquivo foi reservado e o Airflow, disparado (PROCESSANDO).
 * O resultado final (PROCESSADO, REJEITADO, AGUARDANDO_MAPEAMENTO) chega depois, pelo status do
 * arquivo.
 */
public record ProcessamentoResponse(Long arquivoId, String status) {

    public static ProcessamentoResponse disparado(long arquivoId) {
        return new ProcessamentoResponse(arquivoId, "PROCESSANDO");
    }
}
