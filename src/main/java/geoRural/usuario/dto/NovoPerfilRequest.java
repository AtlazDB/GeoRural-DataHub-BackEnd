package geoRural.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Corpo do POST /usuarios/perfis: um perfil novo que junta perfis básicos.
 * Ex.: { "nome": "COORDENADOR", "descricao": "...", "inclui": ["ANALISTA", "AUDITOR"] }
 */
public record NovoPerfilRequest(
        @NotBlank(message = "Informe o nome do perfil.")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,29}$",
                message = "O nome do perfil deve começar com letra e ter de 3 a 30 letras, números ou sublinhado.")
        String nome,
        @Size(max = 255, message = "A descrição pode ter no máximo 255 caracteres.") String descricao,
        @NotEmpty(message = "Escolha pelo menos um perfil para o perfil novo incluir.") Set<String> inclui) {
}
