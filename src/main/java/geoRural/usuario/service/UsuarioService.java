package geoRural.usuario.service;

import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.UsuarioResponse;

import java.util.List;
import java.util.Set;

public interface UsuarioService {

    /** Página de usuários (pagina começa em 0; tamanho máximo 50). busca é opcional. */
    List<UsuarioResponse> listar(String busca, int pagina, int tamanho);

    UsuarioResponse buscar(String id);

    /**
     * Define quais perfis do GeoRural o usuário tem (substitui o conjunto atual).
     *
     * @param idLogado id (sub) de quem está pedindo, para impedir que o gestor se tranque para fora
     */
    UsuarioResponse definirPerfis(String id, Set<String> perfis, String idLogado);

    MunicipiosResponse municipios(String id);

    /** Troca as cidades liberadas do usuário. todas = true ou lista vazia libera o Paraná inteiro. */
    MunicipiosResponse definirMunicipios(String id, MunicipiosRequest pedido, String liberadoPor);
}