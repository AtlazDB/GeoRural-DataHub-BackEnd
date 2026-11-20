package geoRural.usuario.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Role de realm com descrição e atributos (GET /roles?briefRepresentation=false). O atributo
 * "georural" = "perfil" marca os perfis combinados criados pela tela do gestor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KcPerfilCompleto(
        String id,
        String name,
        String description,
        Boolean composite,
        Map<String, List<String>> attributes) {
}
