package geoRural.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Duas versões de um imóvel lado a lado: os campos, a geometria e os indicadores.
 * Em cada indicador, motivo diz de onde veio a diferença: DADO, REGRA, DADO_E_REGRA, NENHUM ou
 * SEM_CALCULO (o indicador não foi calculado numa das versões).
 */
public record ComparacaoImovelResponse(
        String codImovel,
        VersaoImovelResponse de,
        VersaoImovelResponse para,
        List<Campo> campos,
        boolean geometriaMudou,
        BigDecimal areaGeometriaDeHa,
        BigDecimal areaGeometriaParaHa,
        List<Indicador> indicadores
) {

    public record Campo(String campo, Object de, Object para, boolean mudou) {
    }

    public record Indicador(
            String indicador,
            String nome,
            String unidade,
            BigDecimal valorDe,
            String statusDe,
            BigDecimal valorPara,
            String statusPara,
            String regraDe,
            String regraPara,
            boolean dadoMudou,
            boolean regraMudou,
            String motivo
    ) {
    }
}
