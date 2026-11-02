package geoRural.mapeamento.controller;

import geoRural.mapeamento.dto.DestinoResponse;
import geoRural.mapeamento.service.DestinoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** As tabelas da Zona Tratada e os campos que o mapeamento pode preencher (tela de mapeamento). */
@RestController
@Tag(name = "Mapeamento")
@RequestMapping("/api/v1/destinos")
public class DestinoController {

    private final DestinoService destinoService;

    public DestinoController(DestinoService destinoService) {
        this.destinoService = destinoService;
    }

    @Operation(summary = "Tabelas de destino do mapeamento e os campos de cada uma")
    @GetMapping
    public List<DestinoResponse> listar() {
        return destinoService.listar();
    }
}
