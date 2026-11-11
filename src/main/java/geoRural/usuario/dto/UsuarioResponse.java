package geoRural.usuario.dto;

import java.util.List;

/** O que a tela de usuários recebe. perfis traz só os 4 perfis do GeoRural. */
public record UsuarioResponse(
        String id,
        String username,
        String nome,
        String email,
        boolean ativo,
        List<String> perfis) {
}