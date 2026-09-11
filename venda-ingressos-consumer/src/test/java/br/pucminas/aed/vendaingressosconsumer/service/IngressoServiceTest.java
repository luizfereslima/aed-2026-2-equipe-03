package br.pucminas.aed.vendaingressosconsumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressosconsumer.domain.IngressoInvalidadoEvent;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:consumer-test-db;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Este teste não precisa de broker. Sem desligar a criação de tópicos, o KafkaAdmin
        // espera cerca de 35 s por um broker que não existe.
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false"
})
class IngressoServiceTest {

    @Autowired
    private IngressoService ingressoService;

    @Autowired
    private EventoProcessadoRepository eventoProcessadoRepository;

    @Autowired
    private IngressoEmitidoRepository ingressoEmitidoRepository;

    @BeforeEach
    void limparBanco() {
        ingressoEmitidoRepository.deleteAll();
        eventoProcessadoRepository.deleteAll();
    }

    @Test
    void deveProcessarMesmoEventoTresVezesComEfeitoUmaVez() {
        IngressoEmitidoEvent evento = new IngressoEmitidoEvent(
                "evento-001",
                OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001",
                "venda-001",
                "evento-comercial-001"
        );

        ingressoService.processar(evento);
        ingressoService.processar(evento);
        ingressoService.processar(evento);

        assertThat(ingressoEmitidoRepository.count()).isEqualTo(1);
        assertThat(ingressoEmitidoRepository.countByEventoId("evento-001")).isEqualTo(1);
        assertThat(eventoProcessadoRepository.count()).isEqualTo(1);
        assertThat(eventoProcessadoRepository.existsById("evento-001")).isTrue();
    }

    @Test
    void deveRecusarEventoSemEventoIdSemGravarNada() {
        IngressoEmitidoEvent semIdentidade = new IngressoEmitidoEvent(
                null,
                OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001",
                "venda-001",
                "evento-comercial-001"
        );

        Throwable erro = catchThrowable(() -> ingressoService.processar(semIdentidade));

        assertThat(erro)
                .as("sem eventoId não há chave de deduplicação, então não há como ser idempotente; "
                        + "a falha é explícita e não repetível, e a mensagem vai para o tópico de descarte")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ingressoEmitidoRepository.count()).isZero();
        assertThat(eventoProcessadoRepository.count()).isZero();
    }

    @Test
    void deveInvalidarIngressoSemRepetirCompensacao() {
        ingressoService.processar(new IngressoEmitidoEvent(
                "emissao-001", OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001", "venda-001", "evento-comercial-001"));

        IngressoInvalidadoEvent invalidacao = new IngressoInvalidadoEvent(
                "invalidacao-001", OffsetDateTime.parse("2026-08-16T10:20:00Z"),
                "ingresso-001", "venda-001", "evento-comercial-001", "cancelamento do evento");

        ingressoService.invalidar(invalidacao);
        ingressoService.invalidar(invalidacao);

        IngressoEmitidoVO ingresso = ingressoEmitidoRepository.findByIngressoId("ingresso-001").orElseThrow();
        assertThat(ingresso.getSituacao()).isEqualTo("INVALIDADO");
        assertThat(ingresso.getMotivoInvalidacao()).isEqualTo("cancelamento do evento");
        assertThat(eventoProcessadoRepository.count()).isEqualTo(2);
    }
}
