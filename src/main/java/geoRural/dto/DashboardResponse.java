
package geoRural.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardResponse(
        Resumo resumo,
        List<ExecucaoRecente> execucoesRecentes
) {

    public record Resumo(
            long fontesAtivas,
            long execucoesUltimos7Dias,
            long execucoesComFalha,
            long registrosQuarentena,
            long registrosZonaTratada
    ) {}

    public record ExecucaoRecente(
            long id,
            String sigla,
            String fonte,
            String origem,
            String tipo,
            String status,
            Integer registrosLidos,
            Integer registrosValidos,
            Integer registrosInvalidos,
            OffsetDateTime iniciadaEm
    ) {}
}
