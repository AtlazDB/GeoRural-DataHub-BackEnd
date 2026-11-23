package geoRural.dto;

/**
 * Resultado do "Executar agora" de uma fonte por link. {@code resultado} é sempre SOLICITADA: a
 * DAG coletar_fonte foi disparada, pagina o serviço e grava o arquivo; o resultado aparece na fonte
 * (ultimaColetaMensagem).
 */
public record ColetaResponse(String resultado, String mensagem) {

    public static ColetaResponse solicitada() {
        return new ColetaResponse("SOLICITADA",
                "Coleta enviada ao Airflow: o andamento e o resultado aparecem na fonte.");
    }
}
