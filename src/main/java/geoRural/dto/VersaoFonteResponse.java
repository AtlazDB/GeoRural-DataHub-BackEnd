package geoRural.dto;

import java.time.OffsetDateTime;

/**
 * Uma versão de um conjunto de dados da fonte. novos, alterados e removidos só vêm preenchidos
 * nos conjuntos que têm histórico (imóveis rurais); nos outros ficam nulos.
 */
public record VersaoFonteResponse(
        long id,
        String conjunto,
        int versao,
        boolean atual,
        OffsetDateTime processadoEm,
        Long execucaoId,
        Long arquivoId,
        String arquivo,
        String hash,
        String origem,
        Integer registros,
        Integer novos,
        Integer alterados,
        Integer removidos
) {
}
