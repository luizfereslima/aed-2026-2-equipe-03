package br.pucminas.aed.vendaingressosconsumer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class IngressoEmitidoEventTest {

    @Test
    void deveIgnorarCamposDesconhecidosDoPublisher() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String json = """
                {
                  "eventoId": "evento-001",
                  "ocorridoEm": "2026-08-16T10:15:30Z",
                  "ingressoId": "ingresso-001",
                  "vendaId": "venda-001",
                  "eventoComercialId": "evento-comercial-001",
                  "setorId": "setor-a",
                  "assentoId": "assento-a-10"
                }
                """;

        IngressoEmitidoEvent evento = objectMapper.readValue(json, IngressoEmitidoEvent.class);

        assertThat(evento.getEventoId()).isEqualTo("evento-001");
        assertThat(evento.getIngressoId()).isEqualTo("ingresso-001");
        assertThat(evento.getVendaId()).isEqualTo("venda-001");
        assertThat(evento.getEventoComercialId()).isEqualTo("evento-comercial-001");
    }
}
