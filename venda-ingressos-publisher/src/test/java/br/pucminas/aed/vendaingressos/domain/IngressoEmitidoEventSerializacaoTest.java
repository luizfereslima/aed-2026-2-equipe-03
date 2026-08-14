package br.pucminas.aed.vendaingressos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Contrato no fio: valida o JSON que sai do serializador realmente configurado em
 * application.yml, e não o de um ObjectMapper montado no teste.
 */
class IngressoEmitidoEventSerializacaoTest {

    private static final String TOPICO = "ingressos.ingresso-emitido.v1";

    @Test
    void deveSerializarOcorridoEmComoTextoIso8601() throws Exception {
        JsonNode json = serializar(eventoDeExemplo());

        assertThat(json.get("ocorridoEm").isTextual())
                .as("ocorridoEm deve trafegar como texto ISO-8601; número epoch quebra o consumer")
                .isTrue();
        assertThat(json.get("ocorridoEm").asText()).isEqualTo("2026-08-16T10:15:30Z");
    }

    @Test
    void devePublicarSomenteOsCamposDoContrato() throws Exception {
        JsonNode json = serializar(eventoDeExemplo());

        List<String> campos = new ArrayList<>();
        json.fieldNames().forEachRemaining(campos::add);

        assertThat(campos).containsExactlyInAnyOrder(
                "eventoId",
                "ocorridoEm",
                "ingressoId",
                "vendaId",
                "eventoComercialId",
                "setorId",
                "assentoId"
        );
    }

    private JsonNode serializar(IngressoEmitidoEvent evento) throws Exception {
        try (JsonSerializer<IngressoEmitidoEvent> serializer = new JsonSerializer<>()) {
            serializer.configure(Map.of(JsonSerializer.ADD_TYPE_INFO_HEADERS, false), false);
            return new ObjectMapper().readTree(serializer.serialize(TOPICO, evento));
        }
    }

    private IngressoEmitidoEvent eventoDeExemplo() {
        return new IngressoEmitidoEvent(
                "evento-001",
                OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001",
                "venda-001",
                "evento-comercial-001",
                "setor-a",
                "assento-a-10"
        );
    }
}
