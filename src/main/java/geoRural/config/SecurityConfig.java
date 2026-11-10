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
 * Segurança da API (US10). O back é resource server do Keycloak: toda rota exige token válido e,
 * a partir da T23, cada rota é liberada só para os perfis da matriz abaixo.
 *
 * Regras avaliadas de cima para baixo: a PRIMEIRA que casar vale, então as mais específicas vêm antes.
 * O GESTOR entra em todas as regras (acesso total). Rota que não estiver na matriz fica só para o GESTOR.
 *
 * Matriz (rascunho a validar com o PO):
 *   Consulta territorial e indicadores (leitura) ..... ANALISTA, AUDITOR, OPERADOR_DADOS, GESTOR
 *   Fontes, arquivos, mapeamentos, quarentena (leitura) AUDITOR, OPERADOR_DADOS, GESTOR
 *   Fontes, arquivos, mapeamentos, quarentena (escrita) OPERADOR_DADOS, GESTOR
 *   Domínios, destinos, referências cruzadas ......... OPERADOR_DADOS, GESTOR
 *   Criar/calcular/desativar indicador e pipeline .... OPERADOR_DADOS, GESTOR
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String OPERADOR = "OPERADOR_DADOS";
    private static final String ANALISTA = "ANALISTA";
    private static final String AUDITOR = "AUDITOR";
    private static final String GESTOR = "GESTOR";

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, SegurancaErroHandler erros) throws Exception {
        http
                // API stateless com token no header: sem cookie de sessão, então CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                // Usa a configuração do CorsConfig (WebMvcConfigurer), sem duplicar origens.
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        // --- Públicas ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/docs", "/api/v1/docs/**",
                                "/api/v1/swagger-ui/**", "/api/v1/swagger-ui.html", "/api/v1/webjars/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        // Página de erro do Spring: sem isto, um 404 vira 403/401 e esconde o erro de verdade.
                        .requestMatchers("/error").permitAll()

                        // --- Consulta territorial e indicadores (somente leitura) ---
                        // Inclui /imoveis/{cod}/indicadores e o histórico.
                        .requestMatchers(HttpMethod.GET, "/api/v1/focos/**", "/api/v1/imoveis/**",
                                "/api/v1/municipios/**", "/api/v1/indicators/**")
                        .hasAnyRole(ANALISTA, AUDITOR, OPERADOR, GESTOR)

                        // --- Criar, calcular e desativar indicador; pipeline ---
                        .requestMatchers("/api/v1/indicators/**", "/api/v1/pipeline/**")
                        .hasAnyRole(OPERADOR, GESTOR)

                        // --- Apoio ao mapeamento de colunas ---
                        .requestMatchers("/api/v1/destinos/**", "/api/v1/dominios/**",
                                "/api/v1/referencias-cruzadas/**")
                        .hasAnyRole(OPERADOR, GESTOR)

                        // --- Fontes, arquivos, mapeamentos (/fontes/{id}/mapeamentos) e quarentena ---
                        // Leitura: o AUDITOR também acompanha (rastreabilidade).
                        .requestMatchers(HttpMethod.GET, "/api/v1/fontes/**", "/api/v1/arquivos/**",
                                "/api/v1/quarentena/**")
                        .hasAnyRole(AUDITOR, OPERADOR, GESTOR)
                        // Escrita (POST, PUT, DELETE): só quem opera os dados.
                        .requestMatchers("/api/v1/fontes/**", "/api/v1/arquivos/**", "/api/v1/quarentena/**")
                        .hasAnyRole(OPERADOR, GESTOR)

                        // --- Qualquer rota fora da matriz fica só para o GESTOR ---
                        .anyRequest().hasRole(GESTOR))
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