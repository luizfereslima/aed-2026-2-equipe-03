package br.pucminas.aed.vendaingressos.service;

import br.pucminas.aed.vendaingressos.domain.IngressoEmitidoEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
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
    }

    private void adicionarHeader(ProducerRecord<String, IngressoEmitidoEvent> record, String nome, String valor) {
        record.headers().add(nome, valor.getBytes(StandardCharsets.UTF_8));
    }
}
