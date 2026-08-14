package br.pucminas.aed.vendaingressos.controller;

import br.pucminas.aed.vendaingressos.service.IngressoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
