package geoRural.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Criar ou editar um grupo: o nome e as fontes que ficam dentro dele (substitui a lista atual).
 * Uma fonte só fica em um grupo: entrar aqui tira ela do grupo anterior.
 */
public record FonteGrupoRequest(
        @NotBlank(message = "Informe o nome do grupo.")
        @Size(max = 120, message = "O nome do grupo pode ter no máximo 120 caracteres.")
        String nome,
        List<Long> fonteIds) {
}
