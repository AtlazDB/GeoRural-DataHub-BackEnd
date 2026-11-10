package geoRural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * T22: o back vira resource server do Keycloak. Toda rota exige um token válido (assinatura, issuer e
 * validade conferidos contra o realm georural), exceto OPTIONS (CORS) e a documentação do Swagger.
 * As regras por perfil (quem pode o quê) entram na T23.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, SegurancaErroHandler erros) throws Exception {
        http
                // API stateless com token no header: sem cookie de sessão, então CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                // Usa a configuração do CorsConfig (WebMvcConfigurer), sem duplicar origens.
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/docs", "/api/v1/docs/**",
                            "/api/v1/swagger-ui/**", "/api/v1/swagger-ui.html", "/api/v1/webjars/**",
                            "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeJwt()))
                        .authenticationEntryPoint(erros)
                        .accessDeniedHandler(erros))
                .exceptionHandling(erro -> erro
                        .authenticationEntryPoint(erros)
                        .accessDeniedHandler(erros));
        return http.build();
    }

    private JwtAuthenticationConverter conversorDeJwt() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return conversor;
    }
}