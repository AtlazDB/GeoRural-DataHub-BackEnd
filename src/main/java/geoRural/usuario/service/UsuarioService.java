package geoRural.usuario.service;

import geoRural.usuario.dto.MunicipiosRequest;
import geoRural.usuario.dto.NovoPerfilRequest;
import geoRural.usuario.dto.PerfilResponse;
import geoRural.usuario.dto.NovoUsuarioRequest;
import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.dto.UsuarioResponse;

import java.util.List;
import java.util.Set;

public interface UsuarioService {

    /** Página de usuários (pagina começa em 0; tamanho máximo 50). busca é opcional. */
    List<UsuarioResponse> listar(String busca, int pagina, int tamanho);

    UsuarioResponse buscar(String id);

    /** Cria o usuário no Keycloak com os perfis e as cidades; liberadoPor é o login de quem criou. */
    UsuarioResponse criar(NovoUsuarioRequest pedido, String liberadoPor);

    /**
     * Define quais perfis do GeoRural o usuário tem (substitui o conjunto atual).
     *
     * @param idLogado id (sub) de quem está pedindo, para impedir que o gestor se tranque para fora
     */
    UsuarioResponse definirPerfis(String id, Set<String> perfis, String idLogado);

    /**
     * Liga ou desliga o acesso do usuário (enabled no Keycloak). Desativado, ele não consegue entrar;
     * perfis e cidades ficam guardados para quando voltar.
     *
     * @param idLogado id (sub) de quem está pedindo: o gestor não pode desativar a si mesmo
     */
    UsuarioResponse alterarAtivo(String id, boolean ativo, String idLogado);

    /**
     * Define uma senha provisória nova para o usuário (perda ou esquecimento). No próximo login o
     * Keycloak pede que a pessoa escolha outra.
     */
    void redefinirSenha(String id, String senhaProvisoria);

    /** Os perfis que a tela pode atribuir: os 4 do sistema e os combinados criados pelo gestor. */
    List<PerfilResponse> listarPerfis();

    /**
     * Cria um perfil combinado (role composta no Keycloak) que junta perfis do sistema. Quem o
     * recebe ganha tudo o que eles podem; as regras de segurança não mudam.
     */
    PerfilResponse criarPerfil(NovoPerfilRequest pedido);

    /** Muda a descrição de um perfil combinado. Os 4 do sistema não mudam por aqui. */
    PerfilResponse editarDescricaoPerfil(String nome, String descricao);

    MunicipiosResponse municipios(String id);

    /** Troca as cidades liberadas do usuário. todas = true ou lista vazia libera o Paraná inteiro. */
    MunicipiosResponse definirMunicipios(String id, MunicipiosRequest pedido, String liberadoPor);
}