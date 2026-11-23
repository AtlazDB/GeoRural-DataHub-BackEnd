package geoRural.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import geoRural.dto.ComparacaoImovelResponse;
import geoRural.dto.OrigemImovelResponse;
import geoRural.dto.VersaoFonteResponse;
import geoRural.service.VersaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Versões", description = "Histórico de versões dos dados e origem de cada número (US09)")
public class VersaoController {

    private final VersaoService service;

    public VersaoController(VersaoService service) {
        this.service = service;
    }

    @GetMapping("/fontes/{id}/versoes")
    @Operation(summary = "Versões dos conjuntos de dados de uma fonte",
            description = "Data, arquivo de origem (hash), execução e registros de cada versão. No CAR, também "
                    + "quantos imóveis a versão trouxe de novos, alterou e removeu.")
    public List<VersaoFonteResponse> versoesDaFonte(@PathVariable Long id) {
        return service.versoesDaFonte(id);
    }

    @GetMapping("/imoveis/{codImovel}/origem")
    @Operation(summary = "De onde veio este número",
            description = "Trilha da versão atual do imóvel até o arquivo de origem, todas as versões dele e os "
                    + "indicadores com a regra e as versões de dados usadas no cálculo.")
    public OrigemImovelResponse origem(@PathVariable String codImovel) {
        return service.origem(codImovel);
    }

    @GetMapping("/imoveis/{codImovel}/comparar")
    @Operation(summary = "Compara duas versões de um imóvel",
            description = "Campos e geometria que mudaram e, em cada indicador, se a diferença veio do dado, da regra "
                    + "ou dos dois. Sem de/para, compara a versão mais nova com a anterior.")
    public ComparacaoImovelResponse comparar(
            @PathVariable String codImovel,
            @Parameter(description = "id da versão de dados (dataset_versao) mais antiga") @RequestParam(required = false) Long de,
            @Parameter(description = "id da versão de dados (dataset_versao) mais nova") @RequestParam(required = false) Long para) {
        return service.comparar(codImovel, de, para);
    }
}
