package geoRural.controller;

import geoRural.dto.*;
import geoRural.entity.RegistroQuarentena;
import geoRural.service.QuarentenaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Quarentena", description = "Registros reprovados na validação: análise, correção e revalidação")
@RequestMapping("/api/v1/quarentena")
public class QuarentenaController {

    private final QuarentenaService service;

    public QuarentenaController(QuarentenaService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os registros da quarentena, com filtros e paginação")
    @GetMapping
    public ResponseEntity<Page<RegistroQuarentena>> listar(
            @RequestParam(required = false) String tipoErro,
            @RequestParam(required = false) Long fonteId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String texto,
            Pageable pageable) {

        QuarentenaFiltroDTO filtro = new QuarentenaFiltroDTO(tipoErro, fonteId, status, texto);
        return ResponseEntity.ok(service.listar(filtro, pageable));
    }

    @Operation(summary = "Contagens da quarentena por status")
    @GetMapping("/metricas")
    public ResponseEntity<QuarentenaMetricasDTO> obterMetricas() {
        return ResponseEntity.ok(service.obterMetricas());
    }

    @Operation(summary = "Um registro da quarentena")
    @GetMapping("/{id}")
    public ResponseEntity<RegistroQuarentena> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    /** GeoJSON da geometria como chegou; 204 quando ela nem pôde ser lida (GEOMETRIA_MALFORMADA). */
    @Operation(summary = "Geometria do registro como chegou (GeoJSON)")
    @GetMapping("/{id}/geometria")
    public ResponseEntity<Map<String, Object>> buscarGeometria(@PathVariable Long id) {
        Map<String, Object> geometria = service.buscarGeometria(id);
        return geometria == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(geometria);
    }

    /** GeoJSON da geometria salva pelo operador; 204 enquanto não houver correção. */
    @Operation(summary = "Geometria salva pelo operador (GeoJSON)")
    @GetMapping("/{id}/geometria-corrigida")
    public ResponseEntity<Map<String, Object>> buscarGeometriaCorrigida(@PathVariable Long id) {
        Map<String, Object> geometria = service.findCorrectedGeometry(id);
        return geometria == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(geometria);
    }

    /** Prévia da correção automática, em GeoJSON. Não grava nada. */
    @Operation(summary = "Prévia da correção automática da geometria (não grava)")
    @PostMapping("/{id}/geometria/correcao-automatica")
    public ResponseEntity<Map<String, Object>> sugerirCorrecaoGeometria(@PathVariable Long id) {
        return ResponseEntity.ok(service.suggestGeometryFix(id));
    }

    @Operation(summary = "Salva a geometria corrigida pelo operador")
    @PutMapping("/{id}/geometria-corrigida")
    public ResponseEntity<RegistroQuarentena> salvarGeometriaCorrigida(
            @PathVariable Long id,
            @Valid @RequestBody GeometryCorrectionRequest dto) {
        return ResponseEntity.ok(service.saveCorrectedGeometry(id, dto));
    }

    @Operation(summary = "Marca o registro como em análise")
    @PostMapping("/{id}/iniciar-analise")
    public ResponseEntity<RegistroQuarentena> iniciarAnalise(@PathVariable Long id) {
        return ResponseEntity.ok(service.iniciarAnalise(id));
    }

    @Operation(summary = "Descarta o registro, com justificativa")
    @PostMapping("/{id}/descartar")
    public ResponseEntity<RegistroQuarentena> descartar(
            @PathVariable Long id, 
            @Valid @RequestBody DescarteRequestDTO dto) {
        return ResponseEntity.ok(service.descartar(id, dto));
    }

    @Operation(summary = "Salva a correção dos atributos do registro")
    @PostMapping("/{id}/corrigir")
    public ResponseEntity<RegistroQuarentena> corrigir(
            @PathVariable Long id, 
            @Valid @RequestBody CorrecaoRequestDTO dto) {
        return ResponseEntity.ok(service.corrigir(id, dto));
    }

    @Operation(summary = "Revalida o registro e, se passar, grava na Zona Tratada")
    @PostMapping("/{id}/revalidar")
    public ResponseEntity<RevalidationResponse> revalidar(@PathVariable Long id) {
        return ResponseEntity.ok(service.revalidar(id));
    }
}