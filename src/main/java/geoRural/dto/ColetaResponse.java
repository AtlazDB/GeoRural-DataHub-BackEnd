package geoRural.dto;

/**
 * Resultado do "Executar agora" de uma fonte por link. {@code resultado} é sempre SOLICITADA: o
 * pedido ficou na fila da DAG coleta_fontes, que pagina o serviço e grava o arquivo; o resultado
 * aparece na fonte (ultimaColetaMensagem).
 */
public record ColetaResponse(String resultado, String mensagem) {

    public static ColetaResponse solicitada() {
        return new ColetaResponse("SOLICITADA",
                "Coleta na fila do Airflow: começa em até 2 minutos, e o resultado aparece na fonte.");
    }
}
