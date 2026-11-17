package geoRural.usuario.dto;

import java.util.Set;

/**
 * Cidades que o usuário pode consultar. todas = true (ou lista vazia) libera o Paraná inteiro;
 * senão, só os códigos IBGE da lista. Só restringe quem tem o perfil ANALISTA.
 */
public record MunicipiosRequest(boolean todas, Set<String> municipios) {
}
