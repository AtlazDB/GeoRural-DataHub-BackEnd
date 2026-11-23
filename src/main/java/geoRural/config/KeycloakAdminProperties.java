package geoRural.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credencial técnica do back no Keycloak (T26): client georural-backend, com client credentials.
 * Valores vêm de keycloak.admin.* no application.properties.
 */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret) {

    public KeycloakAdminProperties {
        exigir(baseUrl, "keycloak.admin.base-url");
        exigir(realm, "keycloak.admin.realm");
        exigir(clientId, "keycloak.admin.client-id");
        exigir(clientSecret, "keycloak.admin.client-secret");
    }

    private static void exigir(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Propriedade obrigatória ausente: " + nome);
        }
    }
}