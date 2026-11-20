package geoRural.usuario.dto;

import jakarta.validation.constraints.Size;

/** Corpo do PUT /usuarios/perfis/{nome}: a nova descrição do perfil combinado (vazia apaga). */
public record DescricaoPerfilRequest(
        @Size(max = 255, message = "A descrição pode ter no máximo 255 caracteres.") String descricao) {
}
