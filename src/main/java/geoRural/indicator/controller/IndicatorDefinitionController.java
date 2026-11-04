package geoRural.indicator.controller;

import geoRural.indicator.dto.IndicatorDefinitionRequest;
import geoRural.indicator.dto.IndicatorDefinitionResponse;
import geoRural.indicator.service.IndicatorDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indicadores cadastrados pelo usuário (US13). O cálculo é da DAG do Airflow (GeoPandas);
 * aqui só o cadastro e a consulta.
 */
@RestController
@Tag(name = "Indicadores cadastrados", description = "Cadastro de indicadores com modelo de cálculo pronto")
@RequestMapping("/api/v1/indicators")
public class IndicatorDefinitionController {

    private final IndicatorDefinitionService service;

    public IndicatorDefinitionController(IndicatorDefinitionService service) {
        this.service = service;
    }

    /** 201 com o indicador · 400 {mensagem, campos} · 409 sigla já usada. */
    @Operation(summary = "Cadastra um indicador")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IndicatorDefinitionResponse create(@Valid @RequestBody IndicatorDefinitionRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Lista os indicadores cadastrados, ativos e desativados")
    @GetMapping
    public List<IndicatorDefinitionResponse> findAll() {
        return service.findAll();
    }

    /** 200 · 404. */
    @Operation(summary = "Um indicador pela sigla")
    @GetMapping("/{code}")
    public IndicatorDefinitionResponse findByCode(@PathVariable String code) {
        return service.findByCode(code);
    }

    /** 204 · 404. Não apaga: os resultados já calculados continuam apontando para ele. */
    @Operation(summary = "Desativa um indicador (não apaga)")
    @PostMapping("/{code}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String code) {
        service.deactivate(code);
    }
}
