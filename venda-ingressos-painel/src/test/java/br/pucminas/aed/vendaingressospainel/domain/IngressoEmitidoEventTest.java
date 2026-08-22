package br.pucminas.aed.vendaingressospainel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * A regra de compatibilidade FULL do contrato, exercida: o painel declara três campos e
 * recebe os sete que o publisher emite hoje. Campo novo no produtor não obriga a implantar
 * este serviço antes.
 */
class IngressoEmitidoEventTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void deveDesserializarCargaCompletaIgnorandoOsCamposQueNaoUsa() throws Exception {
        String carga = """
                {
                  "eventoId": "3f2b9c14-6a1e-4c77-9c1d-0b6f2a8e4d55",
                  "ocorridoEm": "2026-08-16T10:15:30Z",
                  "ingressoId": "ingresso-001",
                  "vendaId": "venda-001",
                  "eventoComercialId": "evento-comercial-001",
                  "setorId": "setor-a",
                  "assentoId": "assento-a-10"
                }
                """;

        IngressoEmitidoEvent evento = objectMapper.readValue(carga, IngressoEmitidoEvent.class);

        assertThat(evento.getEventoId()).isEqualTo("3f2b9c14-6a1e-4c77-9c1d-0b6f2a8e4d55");
        assertThat(evento.getOcorridoEm()).isEqualTo(OffsetDateTime.parse("2026-08-16T10:15:30Z"));
        assertThat(evento.getEventoComercialId()).isEqualTo("evento-comercial-001");
    }

    @Test
    void deveTolerarCampoNovoIntroduzidoPeloPublisher() throws Exception {
        String carga = """
                {
                  "eventoId": "evento-001",
                  "ocorridoEm": "2026-08-16T10:15:30Z",
                  "eventoComercialId": "evento-comercial-001",
                  "canalDeVenda": "APLICATIVO"
                }
                """;

        IngressoEmitidoEvent evento = objectMapper.readValue(carga, IngressoEmitidoEvent.class);

        assertThat(evento.getEventoComercialId())
                .as("campo desconhecido é ignorado, então o produtor pode ser implantado primeiro")
                .isEqualTo("evento-comercial-001");
    }

    @Test
    void deveLerOcorridoEmComOffsetDiferenteDeUtcComoOMesmoInstante() throws Exception {
        String carga = """
                {
                  "eventoId": "evento-001",
                  "ocorridoEm": "2026-08-16T07:15:30-03:00",
                  "eventoComercialId": "evento-comercial-001"
                }
                """;

        IngressoEmitidoEvent evento = objectMapper.readValue(carga, IngressoEmitidoEvent.class);

        assertThat(evento.getOcorridoEm().toInstant())
                .as("o contrato é ISO-8601 com offset; a janela é calculada sobre o instante, não sobre o texto")
                .isEqualTo(OffsetDateTime.parse("2026-08-16T10:15:30Z").toInstant());
    }

}
