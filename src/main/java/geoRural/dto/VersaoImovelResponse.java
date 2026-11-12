package geoRural.dto;

import java.time.OffsetDateTime;

/**
 * Uma versão de um imóvel e o caminho até a origem dela: fonte, arquivo da zona bruta (hash) e
 * execução. situacao: ATUAL (a linha do imovel_rural), ALTERADO ou REMOVIDO (do histórico).
 */
public record VersaoImovelResponse(
        long datasetVersaoId,
        String conjunto,
        int versao,
        String situacao,
        OffsetDateTime processadoEm,
        Long execucaoId,
        Long arquivoId,
        String arquivo,
        String hash,
        String fonte,
        String fonteNome
) {
}
