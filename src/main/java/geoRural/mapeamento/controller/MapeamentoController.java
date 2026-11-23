package geoRural.mapeamento.controller;

import geoRural.mapeamento.dto.MapeamentoRequest;
import geoRural.mapeamento.dto.MapeamentoResponse;
import geoRural.mapeamento.service.MapeamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Mapeamento", description = "Como cada coluna da fonte chega à Zona Tratada: destino, tipo e regras")
@RequestMapping("/api/v1/fontes/{fonteId}/mapeamentos")
public class MapeamentoController {

    private final MapeamentoService mapeamentoService;

    public MapeamentoController(MapeamentoService mapeamentoService) {
        this.mapeamentoService = mapeamentoService;
    }

    @Operation(summary = "Mapeamento das colunas da fonte")
    @GetMapping
    public List<MapeamentoResponse> listar(@PathVariable Long fonteId) {
        return mapeamentoService.listar(fonteId).stream()
                .map(MapeamentoResponse::de)
                .toList();
    }

    @Operation(summary = "Mapeia uma coluna da fonte")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MapeamentoResponse criar(@PathVariable Long fonteId, @RequestBody MapeamentoRequest request) {
        return MapeamentoResponse.de(mapeamentoService.criar(fonteId, request));
    }

    /** Edita destino e regras (a coluna não muda). 200 · 400 · 404 · 409. */
    @Operation(summary = "Edita o destino e as regras de uma coluna")
    @PutMapping("/{mapeamentoId}")
    public MapeamentoResponse atualizar(@PathVariable Long fonteId, @PathVariable Long mapeamentoId,
                                        @RequestBody MapeamentoRequest request) {
        return MapeamentoResponse.de(mapeamentoService.atualizar(fonteId, mapeamentoId, request));
    }

    @Operation(summary = "Remove o mapeamento de uma coluna")
    @DeleteMapping("/{mapeamentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long fonteId, @PathVariable Long mapeamentoId) {
        mapeamentoService.remover(fonteId, mapeamentoId);
    }
}