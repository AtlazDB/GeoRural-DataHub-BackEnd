package geoRural.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respostas de segurança no mesmo formato do ErroResponse ({"mensagem": "..."}):
 * 401 quando não há token (ou ele é inválido/expirado) e 403 quando o perfil não tem permissão.
 * O JSON é escrito à mão de propósito: as mensagens são fixas e assim não dependemos do ObjectMapper.
 */
@Component
public class SegurancaErroHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String MSG_401 = "Autenticação necessária: envie um token válido.";
    private static final String MSG_403 = "Você não tem permissão para acessar este recurso.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, MSG_401);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, MSG_403);
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"mensagem\":\"" + mensagem + "\"}");
    }
}