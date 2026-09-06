package br.pucminas.aed.vendaingressos.controller;

import br.pucminas.aed.vendaingressos.service.IngressoService;
import br.pucminas.aed.vendaingressos.service.PublicacaoIndisponivelException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vendas-ingressos")
public class IngressoController {

    private final IngressoService ingressoService;

    public IngressoController(IngressoService ingressoService) {
        this.ingressoService = ingressoService;
    }

    @PostMapping
    public ResponseEntity<CompraIngressoVO> solicitarCompra(@RequestBody SolicitarCompraVO solicitacao) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ingressoService.solicitarCompra(solicitacao));
    }

    /**
     * O backpressure chegando a quem gera a carga: sem espaço no produtor, responder 202 seria
     * dizer "aceito" sobre um evento que não entrou em lugar nenhum.
     */
    @ExceptionHandler(PublicacaoIndisponivelException.class)
    public ResponseEntity<Void> aoRecusarPorProdutorIndisponivel(PublicacaoIndisponivelException excecao) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .build();
    }
}
