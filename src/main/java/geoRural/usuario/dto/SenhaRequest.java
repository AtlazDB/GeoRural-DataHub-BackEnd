package geoRural.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do POST /usuarios/{id}/senha: a senha provisória nova (a pessoa troca no próximo login). */
public record SenhaRequest(
        @NotBlank(message = "Informe a senha provisória.")
        @Size(min = 8, message = "A senha provisória deve ter pelo menos 8 caracteres.")
        String senhaProvisoria) {
}
