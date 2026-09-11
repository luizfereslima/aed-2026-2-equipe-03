package br.pucminas.aed.vendaingressosconsumer.controller;

import br.pucminas.aed.vendaingressosconsumer.service.IngressoEmitidoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingressos")
public class IngressoConsultaController {
    private final IngressoEmitidoRepository repository;

    public IngressoConsultaController(IngressoEmitidoRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{ingressoId}")
    public ResponseEntity<?> consultar(@PathVariable String ingressoId) {
        return repository.findByIngressoId(ingressoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
