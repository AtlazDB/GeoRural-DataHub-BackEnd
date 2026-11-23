package geoRural.dto;

/**
 * Uma camada que o mapa pode ligar. {@code id} é o que vai em /camadas/{id}/mapa:
 * "reserva-legal" ou "fonte-{id da fonte}" (uma fonte do insumo genérico, ex.: APP).
 * {@code tipo}: RESERVA_LEGAL ou INSUMO. {@code sigla}: a da fonte, só no INSUMO.
 */
public record CamadaResponse(String id, String nome, String tipo, String sigla) {
}
