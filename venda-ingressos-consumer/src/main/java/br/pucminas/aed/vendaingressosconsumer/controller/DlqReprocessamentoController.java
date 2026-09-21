package br.pucminas.aed.vendaingressosconsumer.controller;

import br.pucminas.aed.vendaingressosconsumer.service.DlqReprocessamentoService;
import br.pucminas.aed.vendaingressosconsumer.service.ReprocessamentoDlqResultado;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operacoes/dlq")
public class DlqReprocessamentoController {

    private final DlqReprocessamentoService service;

    public DlqReprocessamentoController(DlqReprocessamentoService service) {
        this.service = service;
    }

    @PostMapping("/reprocessamentos")
    public ResponseEntity<ReprocessamentoDlqResultado> reprocessar(
            @RequestBody ReprocessarDlqVO requisicao
    ) {
        return ResponseEntity.accepted().body(service.reprocessar(
                requisicao.topico(), requisicao.particao(), requisicao.offset()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> requisicaoInvalida(IllegalArgumentException excecao) {
        return ResponseEntity.badRequest().body(excecao.getMessage());
    }
}
