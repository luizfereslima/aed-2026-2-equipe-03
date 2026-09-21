package br.pucminas.aed.vendaingressosconsumer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DlqReprocessamentoServiceTest {

    private final DlqReprocessamentoService service = new DlqReprocessamentoService(
            "localhost:9092",
            "ingressos.ingresso-emitido.v1.dlt-consumer",
            "ingressos.ingresso-invalidado.v1.dlt-consumer",
            "ingressos.ingresso-emitido.v1",
            "ingressos.ingresso-invalidado.v1"
    );

    @Test
    void deveRecusarTopicoForaDaListaDeDlts() {
        assertThatThrownBy(() -> service.reprocessar("ingressos.ingresso-emitido.v1", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não autorizado");
    }

    @Test
    void deveRecusarOffsetNegativo() {
        assertThatThrownBy(() -> service.reprocessar(
                "ingressos.ingresso-emitido.v1.dlt-consumer", 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }
}
