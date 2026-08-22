package br.pucminas.aed.vendaingressospainel.controller;

import br.pucminas.aed.vendaingressospainel.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressospainel.service.PainelVendasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada. Não decide nada sobre a agregação: lê a mensagem, entrega ao
 * service e só confirma o offset depois que ele retorna.
 *
 * <p>O grupo de consumidores é o {@code venda-ingressos-painel}, declarado no
 * {@code application.yml} e distinto do grupo do {@code venda-ingressos-consumer}. Os dois
 * recebem todas as mensagens do tópico; nenhum tira mensagem do outro.
 */
@Component
public class PainelVendasListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PainelVendasListener.class);

    private final PainelVendasService painelVendasService;

    public PainelVendasListener(PainelVendasService painelVendasService) {
        this.painelVendasService = painelVendasService;
    }

    @KafkaListener(topics = "${app.kafka.topico-ingresso-emitido}")
    public void receber(
            IngressoEmitidoEvent evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particao,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        LOGGER.info(
                "Evento recebido no painel particao={} offset={} eventoId={} ocorridoEm={}",
                particao,
                offset,
                evento.getEventoId(),
                evento.getOcorridoEm()
        );
        painelVendasService.registrar(evento);
        acknowledgment.acknowledge();
    }
}
