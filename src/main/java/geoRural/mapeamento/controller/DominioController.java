package geoRural.mapeamento.controller;

import geoRural.mapeamento.dto.DominioResponse;
import geoRural.mapeamento.service.DominioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Mapeamento")
@RequestMapping("/api/v1/dominios")
public class DominioController {

    private final DominioService dominioService;

    public DominioController(DominioService dominioService) {
        this.dominioService = dominioService;
    }

    @Operation(summary = "Domínios de valores usados nas regras")
    @GetMapping
    public List<DominioResponse> listar() {
        return dominioService.listar().stream()
                .map(DominioResponse::de)
                .toList();
    }
}