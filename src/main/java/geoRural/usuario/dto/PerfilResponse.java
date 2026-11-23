package geoRural.usuario.dto;

import java.util.List;

/**
 * Um perfil que a tela de usuários pode atribuir. doSistema = um dos 4 básicos (o que ele pode
 * fazer está no código); os outros são combinações criadas pelo gestor, e inclui diz quais
 * básicos cada um junta. Nos básicos, inclui é o próprio perfil.
 */
public record PerfilResponse(
        String codigo,
        String descricao,
        boolean doSistema,
        List<String> inclui) {
}
