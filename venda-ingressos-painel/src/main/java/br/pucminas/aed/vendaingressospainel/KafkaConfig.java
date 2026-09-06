package br.pucminas.aed.vendaingressospainel;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * O que o painel faz quando uma mensagem não pode ser agregada. Ver ADR-004.
 *
 * <p>O tópico de descarte é próprio deste consumidor: uma carga sem {@code ocorridoEm} é fatal
 * para a janela e irrelevante para a projeção do {@code venda-ingressos-consumer}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic ingressoEmitidoDltTopic(@Value("${app.kafka.topico-ingresso-emitido-dlt}") String nome) {
        return TopicBuilder.name(nome)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** Este módulo é consumidor, mas precisa produzir para o tópico de descarte. */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> configuracao = new HashMap<>();
        configuracao.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuracao.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuracao.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configuracao.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        configuracao.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configuracao));
    }

    /**
     * Retry serve para falha transitória; carga inválida não fica válida por insistência.
     *
     * <p>Falha de desserialização (já fatal por padrão) e {@code IllegalArgumentException} de
     * contrato violado vão direto ao tópico de descarte, sem queimar tentativa.
     */
    @Bean
    public DefaultErrorHandler errorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topico-ingresso-emitido-dlt}") String topicoDeDescarte
    ) {
        DeadLetterPublishingRecoverer encaminhamentoParaDescarte = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (registro, excecao) -> new TopicPartition(topicoDeDescarte, registro.partition())
        );

        ExponentialBackOff espera = new ExponentialBackOff(1_000L, 2.0);
        espera.setMaxElapsedTime(30_000L);

        DefaultErrorHandler tratamentoDeFalha = new DefaultErrorHandler(encaminhamentoParaDescarte, espera);
        tratamentoDeFalha.addNotRetryableExceptions(IllegalArgumentException.class);
        return tratamentoDeFalha;
    }
}
