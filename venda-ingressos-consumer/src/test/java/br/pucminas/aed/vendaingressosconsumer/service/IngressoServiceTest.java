package br.pucminas.aed.vendaingressosconsumer.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:consumer-test-db;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
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
}
