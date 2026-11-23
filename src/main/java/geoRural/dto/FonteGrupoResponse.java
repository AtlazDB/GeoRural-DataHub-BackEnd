package geoRural.dto;

import java.util.List;

/** Um grupo de fontes e os ids das fontes dentro dele. */
public record FonteGrupoResponse(Long id, String nome, List<Long> fonteIds) {
}
