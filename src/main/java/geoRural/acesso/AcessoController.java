package geoRural.acesso;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import geoRural.usuario.dto.MunicipiosResponse;
import geoRural.usuario.repository.UsuarioMunicipioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/acesso")
@Tag(name = "Acesso", description = "Região que o usuário logado pode consultar (US14)")
public class AcessoController {

    private final AcessoTerritorial acesso;
    private final UsuarioMunicipioRepository municipios;

    public AcessoController(AcessoTerritorial acesso, UsuarioMunicipioRepository municipios) {
        this.acesso = acesso;
        this.municipios = municipios;
    }

    @GetMapping
    @Operation(summary = "Cidades que o usuário logado pode consultar",
            description = "todas = true para quem vê o Paraná inteiro: os perfis que não são só ANALISTA e o "
                    + "analista sem nenhuma cidade definida.")
    public MunicipiosResponse meuAcesso() {
        return acesso.analista()
                .map(id -> MunicipiosResponse.de(municipios.listar(id)))
                .orElseGet(() -> MunicipiosResponse.de(List.of()));
    }
}
