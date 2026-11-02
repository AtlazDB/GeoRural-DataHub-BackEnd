package geoRural.controller;

import geoRural.dto.ProcessamentoResponse;
import geoRural.service.ArquivoBrutoService;
import geoRural.service.ProcessamentoArquivoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Arquivos", description = "Arquivos da zona bruta: fila do Airflow e exclusão")
@RequestMapping("/api/v1/arquivos")
public class ArquivoController {

    private final ProcessamentoArquivoService processamentoArquivoService;
    private final ArquivoBrutoService arquivoBrutoService;

    public ArquivoController(ProcessamentoArquivoService processamentoArquivoService,
                             ArquivoBrutoService arquivoBrutoService) {
        this.processamentoArquivoService = processamentoArquivoService;
        this.arquivoBrutoService = arquivoBrutoService;
    }

    /**
     * Reserva o arquivo e dispara a DAG processar_arquivo. A resposta é imediata: o resultado chega
     * depois, pelo status do arquivo. Airflow fora do ar: 503, e o arquivo fica RECEBIDO.
     */
    @Operation(summary = "Dispara no Airflow o processamento do arquivo (PROCESSANDO)")
    @PostMapping("/{id}/processar")
    public ProcessamentoResponse processar(@PathVariable Long id) {
        return processamentoArquivoService.processar(id);
    }

    /** Só para arquivo REJEITADO que nunca gerou versão de dados; senão 409. */
    @Operation(summary = "Exclui um arquivo rejeitado que nunca gerou versão de dados")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        arquivoBrutoService.excluirRejeitado(id);
    }
}
