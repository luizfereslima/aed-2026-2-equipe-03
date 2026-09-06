package br.pucminas.aed.vendaingressos.service;

import br.pucminas.aed.vendaingressos.domain.IngressoEmitidoEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublicacaoIngressoService {

    static final String CE_SPECVERSION = "ce_specversion";
    static final String CE_ID = "ce_id";
    static final String CE_SOURCE = "ce_source";
    static final String CE_TYPE = "ce_type";
    static final String CE_TIME = "ce_time";

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicacaoIngressoService.class);

    private final KafkaTemplate<String, IngressoEmitidoEvent> kafkaTemplate;
    private final String topicName;
    private final String ceSource;
    private final String ceType;

    public PublicacaoIngressoService(
            KafkaTemplate<String, IngressoEmitidoEvent> kafkaTemplate,
            @Value("${app.kafka.topico-ingresso-emitido}") String topicName,
            @Value("${app.kafka.ce-source}") String ceSource,
            @Value("${app.kafka.ce-type-ingresso-emitido}") String ceType
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.ceSource = ceSource;
        this.ceType = ceType;
    }

    public void publicar(IngressoEmitidoEvent evento) {
        ProducerRecord<String, IngressoEmitidoEvent> record =
                new ProducerRecord<>(topicName, evento.getEventoComercialId(), evento);
        adicionarHeader(record, CE_SPECVERSION, "1.0");
        adicionarHeader(record, CE_ID, evento.getEventoId());
        adicionarHeader(record, CE_SOURCE, ceSource);
        adicionarHeader(record, CE_TYPE, ceType);
        adicionarHeader(record, CE_TIME, evento.getOcorridoEm().toString());

        enviar(record, evento);
    }

    /**
     * A falha síncrona (buffer esgotado) recusa a solicitação, porque nada foi aceito. A
     * assíncrona só é registrada: ali a venda já foi confirmada e o dual-write do ADR-002 já
     * aconteceu, e a correção é o outbox previsto para a etapa de Event Sourcing.
     */
    private void enviar(ProducerRecord<String, IngressoEmitidoEvent> record, IngressoEmitidoEvent evento) {
        try {
            kafkaTemplate.send(record).whenComplete((resultado, erro) -> {
                if (erro != null) {
                    LOGGER.error("Falha ao publicar IngressoEmitidoEvent eventoId={}", evento.getEventoId(), erro);
                    return;
                }
                LOGGER.info(
                        "IngressoEmitidoEvent publicado eventoId={} eventoComercialId={}",
                        evento.getEventoId(),
                        evento.getEventoComercialId()
                );
            });
        } catch (KafkaException excecao) {
            LOGGER.error(
                    "Produtor sem espaço para aceitar IngressoEmitidoEvent eventoId={} eventoComercialId={}",
                    evento.getEventoId(),
                    evento.getEventoComercialId(),
                    excecao
            );
            throw new PublicacaoIndisponivelException(
                    "produtor Kafka sem espaço para aceitar o evento; solicitação recusada", excecao);
        }
    }

    private void adicionarHeader(ProducerRecord<String, IngressoEmitidoEvent> record, String nome, String valor) {
        record.headers().add(nome, valor.getBytes(StandardCharsets.UTF_8));
    }
}
