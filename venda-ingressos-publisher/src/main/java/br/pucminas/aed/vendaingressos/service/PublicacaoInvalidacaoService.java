package br.pucminas.aed.vendaingressos.service;

import br.pucminas.aed.vendaingressos.domain.IngressoInvalidadoEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PublicacaoInvalidacaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicacaoInvalidacaoService.class);
    private final KafkaTemplate<String, IngressoInvalidadoEvent> kafkaTemplate;
    private final String topicName;
    private final String ceSource;
    private final String ceType;

    public PublicacaoInvalidacaoService(
            KafkaTemplate<String, IngressoInvalidadoEvent> kafkaTemplate,
            @Value("${app.kafka.topico-ingresso-invalidado}") String topicName,
            @Value("${app.kafka.ce-source}") String ceSource,
            @Value("${app.kafka.ce-type-ingresso-invalidado}") String ceType
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.ceSource = ceSource;
        this.ceType = ceType;
    }

    public IngressoInvalidadoEvent publicar(String ingressoId, String vendaId, String eventoComercialId, String motivo) {
        exigirTexto(ingressoId, "ingressoId");
        exigirTexto(vendaId, "vendaId");
        exigirTexto(eventoComercialId, "eventoComercialId");
        exigirTexto(motivo, "motivo");
        IngressoInvalidadoEvent evento = new IngressoInvalidadoEvent(
                UUID.randomUUID().toString(), OffsetDateTime.now(ZoneOffset.UTC), ingressoId,
                vendaId, eventoComercialId, motivo);
        ProducerRecord<String, IngressoInvalidadoEvent> record =
                new ProducerRecord<>(topicName, evento.getEventoComercialId(), evento);
        adicionarHeader(record, "ce_specversion", "1.0");
        adicionarHeader(record, "ce_id", evento.getEventoId());
        adicionarHeader(record, "ce_source", ceSource);
        adicionarHeader(record, "ce_type", ceType);
        adicionarHeader(record, "ce_time", evento.getOcorridoEm().toString());
        try {
            kafkaTemplate.send(record).whenComplete((resultado, erro) -> {
                if (erro != null) {
                    LOGGER.error("Falha ao publicar IngressoInvalidadoEvent eventoId={}", evento.getEventoId(), erro);
                    return;
                }
                LOGGER.info("IngressoInvalidadoEvent publicado eventoId={} ingressoId={}",
                        evento.getEventoId(), evento.getIngressoId());
            });
        } catch (KafkaException excecao) {
            throw new PublicacaoIndisponivelException(
                    "produtor Kafka sem espaço para aceitar o evento de invalidação", excecao);
        }
        return evento;
    }

    private void exigirTexto(String valor, String nome) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalArgumentException(nome + " é obrigatório");
        }
    }

    private void adicionarHeader(ProducerRecord<String, IngressoInvalidadoEvent> record, String nome, String valor) {
        record.headers().add(nome, valor.getBytes(StandardCharsets.UTF_8));
    }
}
