package geoRural.dto;

import geoRural.entity.ArquivoBruto;

import java.time.OffsetDateTime;

public record ArquivoBrutoResponse(
        Long id,
        String nome,
        long tamanho,
        OffsetDateTime enviadoEm,
        String hash,
        String status,
        String origem,
        Integer progress,
        String progressStep
) {
    public static ArquivoBrutoResponse de(ArquivoBruto a) {
        return new ArquivoBrutoResponse(
                a.getId(),
                a.getNomeOriginal(),
                a.getTamanhoBytes(),
                a.getRecebidoEm(),
                "sha256:" + a.getHashSha256(),
                a.getStatus(),
                a.getOrigem(),
                a.getProgress(),
                a.getProgressStep()
        );
    }
}
