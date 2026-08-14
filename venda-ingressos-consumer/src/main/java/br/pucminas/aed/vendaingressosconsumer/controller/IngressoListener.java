package br.pucminas.aed.vendaingressosconsumer.controller;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressosconsumer.service.IngressoService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class IngressoListener {

    private final IngressoService ingressoService;

    public IngressoListener(IngressoService ingressoService) {
        this.ingressoService = ingressoService;
    }

    @KafkaListener(topics = "${app.kafka.topico-ingresso-emitido}")
    public void receber(IngressoEmitidoEvent evento, Acknowledgment acknowledgment) {
        ingressoService.processar(evento);
        acknowledgment.acknowledge();
    }
}
