package geoRural.controller;

import geoRural.dto.FonteGrupoRequest;
import geoRural.dto.FonteGrupoResponse;
import geoRural.service.FonteGrupoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Grupos de fontes: só organizam a tela (cards dentro de uma "pasta"). Escrita: Operador e Gestor. */
@RestController
@Tag(name = "Fontes", description = "Cadastro das fontes, upload para a zona bruta e coleta por link")
@RequestMapping("/api/v1/fontes/grupos")
public class FonteGrupoController {

    private final FonteGrupoService service;

    public FonteGrupoController(FonteGrupoService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os grupos de fontes com as fontes de cada um")
    @GetMapping
    public List<FonteGrupoResponse> listar() {
        return service.listar();
    }

    /** 201 · 400 · 409 nome repetido. */
    @Operation(summary = "Cria um grupo de fontes")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FonteGrupoResponse criar(@Valid @RequestBody FonteGrupoRequest pedido) {
        return service.criar(pedido);
    }

    /** Renomeia e troca as fontes do grupo. 200 · 400 · 404 · 409. */
    @Operation(summary = "Edita um grupo de fontes (nome e fontes dentro dele)")
    @PutMapping("/{id}")
    public FonteGrupoResponse atualizar(@PathVariable Long id, @Valid @RequestBody FonteGrupoRequest pedido) {
        return service.atualizar(id, pedido);
    }

    /** As fontes ficam sem grupo; nada é apagado. 204 · 404. */
    @Operation(summary = "Apaga um grupo (as fontes ficam sem grupo)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
