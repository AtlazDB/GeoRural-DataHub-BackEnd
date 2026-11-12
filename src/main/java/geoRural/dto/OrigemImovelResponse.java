package geoRural.dto;

import java.util.List;

/**
 * "De onde veio este número?": a versão atual do imóvel com a trilha até o arquivo de origem,
 * todas as versões dele (da mais nova para a mais antiga) e os indicadores com a regra e as
 * versões de dados usadas no cálculo.
 */
public record OrigemImovelResponse(
        String codImovel,
        VersaoImovelResponse versaoAtual,
        List<VersaoImovelResponse> versoes,
        List<IndicadorResponse> indicadores,
        List<IndicadorCadastradoResultado> indicadoresCadastrados
) {
}
