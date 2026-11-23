package geoRural.usuario.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Perfis marcados na tela. Lista vazia é permitida (usuário fica sem acesso). */
public record PerfisRequest(@NotNull(message = "Informe a lista de perfis.") Set<String> perfis) {
}