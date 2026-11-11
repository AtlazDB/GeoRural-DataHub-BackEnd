package geoRural.usuario.exception;

import geoRural.dto.ErroResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz os erros da gestão de usuários (T27) em respostas HTTP no formato {"mensagem": "..."}. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsuarioExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UsuarioExceptionHandler.class);

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> usuarioNaoEncontrado(UsuarioNaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse.de(e.getMessage()));
    }

    @ExceptionHandler(PerfilInvalidoException.class)
    public ResponseEntity<ErroResponse> perfilInvalido(PerfilInvalidoException e) {
        String msg = e.getMessage()
                + ". Perfis aceitos: OPERADOR_DADOS, ANALISTA, AUDITOR, GESTOR.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErroResponse.de(msg));
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ErroResponse> operacaoNaoPermitida(OperacaoNaoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErroResponse.de(e.getMessage()));
    }

    @ExceptionHandler(KeycloakIndisponivelException.class)
    public ResponseEntity<ErroResponse> keycloakIndisponivel(KeycloakIndisponivelException e) {
        log.error("Falha ao falar com o Keycloak: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErroResponse.de("O serviço de autenticação está indisponível. Tente novamente em instantes."));
    }
}