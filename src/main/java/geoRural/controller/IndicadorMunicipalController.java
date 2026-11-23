
package geoRural.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import geoRural.dto.IndicadorMunicipalResponse;
import geoRural.service.IndicadorMunicipalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/indicadores/municipais")
@Tag(
    name = "Indicadores municipais",
    description = "Agregações ambientais por município"
)
public class IndicadorMunicipalController {

    private final IndicadorMunicipalService service;

    public IndicadorMunicipalController(
            IndicadorMunicipalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consulta IRL agregado por município")
    public List<IndicadorMunicipalResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String codIbge) {

        return service.listarIrlMunicipal(estado, codIbge);
    }

    @GetMapping("/exportar")
    @Operation(summary = "Exporta indicadores municipais em CSV")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String codIbge) {

        List<IndicadorMunicipalResponse> dados =
                service.listarIrlMunicipal(estado, codIbge);

        StringBuilder csv = new StringBuilder();

        csv.append("Código IBGE;Município;Estado;Indicador;")
           .append("Total de imóveis;Área de Reserva Legal (ha);")
           .append("Área total dos imóveis (ha);")
           .append("Valor municipal;Conformes;Críticos\n");

        for (IndicadorMunicipalResponse item : dados) {
            csv.append(item.codIbge()).append(";")
               .append(item.municipio()).append(";")
               .append(item.estado()).append(";")
               .append(item.indicador()).append(";")
               .append(item.totalImoveis()).append(";")
               .append(item.areaReservaLegalHa()).append(";")
               .append(item.areaTotalImoveisHa()).append(";")
               .append(item.valorMunicipal()).append(";")
               .append(item.totalConformes()).append(";")
               .append(item.totalCriticos())
               .append("\n");
        }

        byte[] arquivo = csv.toString()
                .getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"indicadores-municipais.csv\""
                )
                .contentType(
                    MediaType.parseMediaType("text/csv;charset=UTF-8")
                )
                .body(arquivo);
    }
}
