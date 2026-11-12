package geoRural.usuario.dto;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST /usuarios. A senha é provisória: o Keycloak pede uma nova no primeiro login.
 * todasCidades e municipios têm o mesmo sentido do PUT /usuarios/{id}/municipios.
 */
public record NovoUsuarioRequest(
        @NotBlank(message = "Informe o login.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "O login deve ter de 3 a 50 letras, números, ponto, hífen ou sublinhado.")
        String username,
        @NotBlank(message = "Informe o nome.") @Size(max = 100) String nome,
        @NotBlank(message = "Informe o sobrenome.") @Size(max = 100) String sobrenome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") String email,
        @NotBlank(message = "Informe a senha provisória.")
        @Size(min = 8, message = "A senha provisória deve ter pelo menos 8 caracteres.")
        String senhaProvisoria,
        @NotNull(message = "Informe a lista de perfis.") Set<String> perfis,
        boolean todasCidades,
        Set<String> municipios) {
}
