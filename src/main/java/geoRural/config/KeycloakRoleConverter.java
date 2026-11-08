package geoRural.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lê as roles do token do Keycloak (claim realm_access.roles) e as entrega ao Spring Security
 * como ROLE_<perfil>. Só os 4 perfis do GeoRural viram permissão: roles padrão do Keycloak
 * (default-roles-georural, offline_access, uma_authorization) são ignoradas de propósito.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> PERFIS = Set.of("OPERADOR_DADOS", "ANALISTA", "AUDITOR", "GESTOR");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .map(String::valueOf)
                .filter(PERFIS::contains)
                .<GrantedAuthority>map(perfil -> new SimpleGrantedAuthority("ROLE_" + perfil))
                .toList();
    }
}