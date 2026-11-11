package geoRural.usuario.exception;

/** A regra de negócio proíbe a operação (ex.: o gestor remover o próprio perfil). Vira HTTP 409. */
public class OperacaoNaoPermitidaException extends RuntimeException {
    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}