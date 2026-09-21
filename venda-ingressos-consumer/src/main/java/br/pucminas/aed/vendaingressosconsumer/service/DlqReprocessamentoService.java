package br.pucminas.aed.vendaingressosconsumer.service;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Reprocessamento manual: uma mensagem explicitamente escolhida volta ao tópico original. */
@Service
public class DlqReprocessamentoService {

    private static final String HEADER_TOPICO_ORIGINAL = "kafka_dlt-original-topic";
    private static final String HEADER_PARTICAO_ORIGINAL = "kafka_dlt-original-partition";
    private static final String HEADER_OFFSET_ORIGINAL = "kafka_dlt-original-offset";
    private static final String HEADER_CE_ID = "ce_id";

    private final String bootstrapServers;
    private final Set<String> topicosDlq;
    private final Set<String> topicosOriginais;

    public DlqReprocessamentoService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topico-ingresso-emitido-dlt}") String topicoEmitidoDlq,
            @Value("${app.kafka.topico-ingresso-invalidado-dlt}") String topicoInvalidadoDlq,
            @Value("${app.kafka.topico-ingresso-emitido}") String topicoEmitido,
            @Value("${app.kafka.topico-ingresso-invalidado}") String topicoInvalidado
    ) {
        this.bootstrapServers = bootstrapServers;
        this.topicosDlq = Set.of(topicoEmitidoDlq, topicoInvalidadoDlq);
        this.topicosOriginais = Set.of(topicoEmitido, topicoInvalidado);
    }

    public ReprocessamentoDlqResultado reprocessar(String topicoDlq, int particao, long offset) {
        validarRequisicao(topicoDlq, particao, offset);
        ConsumerRecord<byte[], byte[]> registro = lerRegistro(topicoDlq, particao, offset);

        String topicoOriginal = textoDoHeader(registro, HEADER_TOPICO_ORIGINAL);
        int particaoOriginal = inteiroDoHeader(registro, HEADER_PARTICAO_ORIGINAL);
        long offsetOriginal = longoDoHeader(registro, HEADER_OFFSET_ORIGINAL);
        String ceId = textoDoHeader(registro, HEADER_CE_ID);
        if (!topicosOriginais.contains(topicoOriginal) || ceId == null || ceId.isBlank()) {
            throw new IllegalArgumentException("DLQ sem tópico original ou ce_id CloudEvents válido");
        }

        publicarNovamente(registro, topicoOriginal, particaoOriginal);
        return new ReprocessamentoDlqResultado(topicoOriginal, particaoOriginal, offsetOriginal, ceId);
    }

    private ConsumerRecord<byte[], byte[]> lerRegistro(String topicoDlq, int particao, long offset) {
        Properties configuracao = new Properties();
        configuracao.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuracao.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        configuracao.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configuracao.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        configuracao.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        TopicPartition topicPartition = new TopicPartition(topicoDlq, particao);
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(configuracao)) {
            consumer.assign(Set.of(topicPartition));
            consumer.seek(topicPartition, offset);
            List<ConsumerRecord<byte[], byte[]>> registros = consumer
                    .poll(Duration.ofSeconds(5))
                    .records(topicPartition);
            return registros.stream()
                    .filter(registro -> registro.offset() == offset)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "mensagem não encontrada na DLQ para o tópico, partição e offset informados"));
        }
    }

    private void publicarNovamente(
            ConsumerRecord<byte[], byte[]> registro,
            String topicoOriginal,
            int particaoOriginal
    ) {
        Properties configuracao = new Properties();
        configuracao.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configuracao.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuracao.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configuracao.put(ProducerConfig.ACKS_CONFIG, "all");
        configuracao.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        ProducerRecord<byte[], byte[]> reprocessamento = new ProducerRecord<>(
                topicoOriginal, particaoOriginal, registro.key(), registro.value());
        registro.headers().forEach(header -> reprocessamento.headers().add(header.key(), header.value()));

        try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(configuracao)) {
            producer.send(reprocessamento).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("reprocessamento interrompido", excecao);
        } catch (Exception excecao) {
            throw new IllegalStateException("não foi possível republicar a mensagem da DLQ", excecao);
        }
    }

    private void validarRequisicao(String topicoDlq, int particao, long offset) {
        if (!topicosDlq.contains(topicoDlq)) {
            throw new IllegalArgumentException("tópico não autorizado para reprocessamento manual");
        }
        if (particao < 0 || offset < 0) {
            throw new IllegalArgumentException("partição e offset devem ser maiores ou iguais a zero");
        }
    }

    private String textoDoHeader(ConsumerRecord<byte[], byte[]> registro, String nome) {
        Header header = registro.headers().lastHeader(nome);
        return header == null || header.value() == null
                ? null
                : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int inteiroDoHeader(ConsumerRecord<byte[], byte[]> registro, String nome) {
        Header header = registro.headers().lastHeader(nome);
        try {
            return ByteBuffer.wrap(header.value()).getInt();
        } catch (RuntimeException excecao) {
            throw new IllegalArgumentException("DLQ sem partição original válida", excecao);
        }
    }

    private long longoDoHeader(ConsumerRecord<byte[], byte[]> registro, String nome) {
        Header header = registro.headers().lastHeader(nome);
        try {
            return ByteBuffer.wrap(header.value()).getLong();
        } catch (RuntimeException excecao) {
            throw new IllegalArgumentException("DLQ sem offset original válido", excecao);
        }
    }
}
