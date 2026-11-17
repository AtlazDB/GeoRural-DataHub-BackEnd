package geoRural.usuario.dto;

import java.util.List;

/** todas = true quando o usuário não tem nenhuma cidade na lista (acesso ao Paraná inteiro). */
public record MunicipiosResponse(boolean todas, List<MunicipioLiberado> municipios) {

    public static MunicipiosResponse de(List<MunicipioLiberado> municipios) {
        return new MunicipiosResponse(municipios.isEmpty(), municipios);
    }
}
