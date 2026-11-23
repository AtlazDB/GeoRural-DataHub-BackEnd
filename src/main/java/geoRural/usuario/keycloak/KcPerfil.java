package geoRural.usuario.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Role de realm como o Keycloak devolve. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KcPerfil(String id, String name) {
}