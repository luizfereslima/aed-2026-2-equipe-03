package br.pucminas.aed.vendaingressospainel.controller;

import br.pucminas.aed.vendaingressospainel.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressospainel.service.PainelVendasService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Confirmar o offset só depois do retorno do service é o que faz o backpressure existir:
 * enquanto este método não termina, o contêiner não busca mais registros.
 */
@Component
public class PainelVendasListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PainelVendasListener.class);

    private final PainelVendasService painelVendasService;
    private final Duration atrasoSimulado;

    public PainelVendasListener(
            PainelVendasService painelVendasService,
            @Value("${app.painel.atraso-simulado}") Duration atrasoSimulado
    ) {
        this.painelVendasService = painelVendasService;
        this.atrasoSimulado = atrasoSimulado;
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
        aguardarAtrasoSimulado();
        painelVendasService.registrar(evento);
        acknowledgment.acknowledge();
    }

    /**
     * Consumidor lento sob demanda, para que a fila apareça: apurando em memória o painel é
     * rápido demais para acumular lag observável. Vem desligado ({@code PT0S}).
     */
    private void aguardarAtrasoSimulado() {
        if (atrasoSimulado.isZero() || atrasoSimulado.isNegative()) {
            return;
        }
        try {
            Thread.sleep(atrasoSimulado.toMillis());
        } catch (InterruptedException interrupcao) {
            Thread.currentThread().interrupt();
        }
    }
}
