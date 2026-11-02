package geoRural.controller;

import geoRural.mapeamento.dto.FonteColunaResponse;
import geoRural.mapeamento.service.FonteColunaService;
import geoRural.dto.ArquivoBrutoResponse;
import geoRural.dto.ColetaResponse;
import geoRural.dto.FonteRequest;
import geoRural.dto.FonteResponse;
import geoRural.dto.TesteLinkRequest;
import geoRural.dto.TesteLinkResponse;
import geoRural.dto.UploadResponse;
import geoRural.entity.ArquivoBruto;
import geoRural.entity.Fonte;
import geoRural.service.ArquivoBrutoService;
import geoRural.service.ColetaLinkService;
import geoRural.service.FonteService;
import geoRural.service.TesteLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Fontes", description = "Cadastro das fontes, upload para a zona bruta e coleta por link")
@RequestMapping("/api/v1/fontes")
public class FonteController {

    private final FonteService fonteService;
    private final ArquivoBrutoService arquivoBrutoService;
    private final FonteColunaService fonteColunaService;
    private final ColetaLinkService coletaLinkService;
    private final TesteLinkService testeLinkService;

    public FonteController(FonteService fonteService, ArquivoBrutoService arquivoBrutoService,
                           FonteColunaService fonteColunaService, ColetaLinkService coletaLinkService,
                           TesteLinkService testeLinkService) {
        this.fonteService = fonteService;
        this.arquivoBrutoService = arquivoBrutoService;
        this.fonteColunaService = fonteColunaService;
        this.coletaLinkService = coletaLinkService;
        this.testeLinkService = testeLinkService;
    }

    @Operation(summary = "Lista as fontes com os arquivos recebidos")
    @GetMapping
    public List<FonteResponse> listar(@RequestParam(required = false) String busca) {
        return fonteService.listar(busca).stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(summary = "Colunas detectadas da fonte")
    @GetMapping("/{id}/colunas")
        public List<FonteColunaResponse> listarColunas(@PathVariable Long id) {
    return fonteColunaService.listar(id);
    }

    @Operation(summary = "Cadastra uma fonte")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FonteResponse cadastrar(@RequestBody FonteRequest request) {
        Fonte fonte = fonteService.cadastrar(request);
        return toResponse(fonte);
    }

    /** Edita a fonte (a sigla não muda). 200 com a fonte · 400 · 404. */
    @Operation(summary = "Edita uma fonte (a sigla não muda)")
    @PutMapping("/{id}")
    public FonteResponse atualizar(@PathVariable Long id, @RequestBody FonteRequest request) {
        return toResponse(fonteService.atualizar(id, request));
    }

    @Operation(summary = "Envia um arquivo para a zona bruta")
    @PostMapping(value = "/{id}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse enviarArquivo(
            @PathVariable Long id,
            @RequestPart("arquivo") MultipartFile arquivo) throws IOException {

        ArquivoBruto recebido = arquivoBrutoService.receber(id, arquivo);

        return new UploadResponse(
                toResponse(recebido.getFonte()),
                ArquivoBrutoResponse.de(recebido)
        );
    }

    /**
     * "Testar link" do formulário de fonte: pede ao link uma amostra pequena e devolve o status,
     * o total de registros, as colunas e os avisos. Link inválido ou interno responde 400; falha
     * do servidor de origem, 502.
     */
    @Operation(summary = "Testa um link: amostra, total e colunas, sem gravar nada")
    @PostMapping("/testar-link")
    public TesteLinkResponse testarLink(@RequestBody TesteLinkRequest request) {
        return testeLinkService.testar(request);
    }

    /**
     * "Executar agora" de uma fonte por link: 202, o pedido fica na fila da DAG coleta_fontes, que
     * pagina o serviço (SOLICITADA). O andamento aparece depois na fonte.
     */
    @Operation(summary = "Pede ao Airflow a coleta do link da fonte")
    @PostMapping("/{id}/executar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ColetaResponse executar(@PathVariable Long id) {
        return coletaLinkService.executar(id);
    }

    private FonteResponse toResponse(Fonte fonte) {
        return FonteResponse.de(fonte, arquivoBrutoService.listarPorFonte(fonte.getId()));
    }
}
