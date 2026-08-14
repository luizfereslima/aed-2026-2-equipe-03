package br.pucminas.aed.vendaingressos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucminas.aed.vendaingressos.domain.IngressoEmitidoEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class PublicacaoIngressoServiceTest {

    @Test
    void devePublicarComCloudEventsBinarioEChaveVendaId() {
        KafkaTemplate<String, IngressoEmitidoEvent> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        PublicacaoIngressoService service = new PublicacaoIngressoService(
                kafkaTemplate,
                "ingressos.ingresso-emitido.v1",
                "/venda-ingressos-publisher",
                "ingressos.ingresso.emitido.v1"
        );
        IngressoEmitidoEvent evento = new IngressoEmitidoEvent(
                "evento-001",
                OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001",
                "venda-001",
                "evento-comercial-001",
                "setor-a",
                "assento-a-10"
        );

        service.publicar(evento);

        ArgumentCaptor<ProducerRecord<String, IngressoEmitidoEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, IngressoEmitidoEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("ingressos.ingresso-emitido.v1");
        assertThat(record.key()).isEqualTo("venda-001");
        assertThat(header(record, "ce_specversion")).isEqualTo("1.0");
        assertThat(header(record, "ce_id")).isEqualTo("evento-001");
        assertThat(header(record, "ce_source")).isEqualTo("/venda-ingressos-publisher");
        assertThat(header(record, "ce_type")).isEqualTo("ingressos.ingresso.emitido.v1");
        assertThat(header(record, "ce_time")).isEqualTo("2026-08-16T10:15:30Z");
    }

    private String header(ProducerRecord<String, IngressoEmitidoEvent> record, String nome) {
        return new String(record.headers().lastHeader(nome).value(), StandardCharsets.UTF_8);
    }
}
