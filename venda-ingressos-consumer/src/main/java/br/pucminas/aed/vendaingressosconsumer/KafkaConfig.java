package br.pucminas.aed.vendaingressosconsumer;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;
import br.pucminas.aed.vendaingressosconsumer.domain.IngressoInvalidadoEvent;

/**
 * O que este consumidor faz quando uma mensagem não pode ser processada. Ver ADR-004.
 */
@Configuration
public class KafkaConfig {

    /**
     * Mesmo número de partições da origem, porque o encaminhamento preserva o número da
     * partição e, com ele, o agrupamento por evento comercial.
     */
    @Bean
    public NewTopic ingressoEmitidoDltTopic(@Value("${app.kafka.topico-ingresso-emitido-dlt}") String nome) {
        return TopicBuilder.name(nome)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ingressoInvalidadoDltTopic(@Value("${app.kafka.topico-ingresso-invalidado-dlt}") String nome) {
        return TopicBuilder.name(nome).partitions(3).replicas(1).build();
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
            @Value("${app.kafka.topico-ingresso-invalidado}") String topicoInvalidado,
            @Value("${app.kafka.topico-ingresso-emitido-dlt}") String topicoEmitidoDlt,
            @Value("${app.kafka.topico-ingresso-invalidado-dlt}") String topicoInvalidadoDlt
    ) {
        DeadLetterPublishingRecoverer encaminhamentoParaDescarte = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (registro, excecao) -> new TopicPartition(
                        registro.topic().equals(topicoInvalidado)
                                ? topicoInvalidadoDlt : topicoEmitidoDlt,
                        registro.partition())
        );

        ExponentialBackOff espera = new ExponentialBackOff(1_000L, 2.0);
        espera.setMaxElapsedTime(30_000L);

        DefaultErrorHandler tratamentoDeFalha = new DefaultErrorHandler(encaminhamentoParaDescarte, espera);
        tratamentoDeFalha.addNotRetryableExceptions(IllegalArgumentException.class);
        return tratamentoDeFalha;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IngressoInvalidadoEvent>
    invalidacaoKafkaListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            DefaultErrorHandler errorHandler
    ) {
        Map<String, Object> configuracao = new HashMap<>();
        configuracao.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuracao.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configuracao.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuracao.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuracao.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        JsonDeserializer<IngressoInvalidadoEvent> jsonDeserializer =
                new JsonDeserializer<>(IngressoInvalidadoEvent.class, false);
        jsonDeserializer.addTrustedPackages("br.pucminas.aed.vendaingressosconsumer.domain");
        ConsumerFactory<String, IngressoInvalidadoEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                configuracao,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
        ConcurrentKafkaListenerContainerFactory<String, IngressoInvalidadoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
