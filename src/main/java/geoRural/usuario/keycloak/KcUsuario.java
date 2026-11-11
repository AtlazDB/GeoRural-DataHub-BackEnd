package geoRural.usuario.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usuário como o Keycloak devolve (só os campos que o GeoRural usa). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KcUsuario(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled) {
}