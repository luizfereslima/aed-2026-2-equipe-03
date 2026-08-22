package br.pucminas.aed.vendaingressospainel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * A janela é alinhada pelo relógio, e não pela hora em que o processo subiu.
 */
class JanelaServiceTest {

    private static final Duration CINCO_MINUTOS = Duration.ofMinutes(5);

    private final JanelaService janelaService = new JanelaService();

    @Test
    void deveAlinharOInicioDaJanelaAoMultiploAnteriorDeCincoMinutos() {
        assertThat(inicioDaJanela("2026-08-16T10:07:33Z")).isEqualTo(OffsetDateTime.parse("2026-08-16T10:05:00Z"));
        assertThat(inicioDaJanela("2026-08-16T10:04:59Z")).isEqualTo(OffsetDateTime.parse("2026-08-16T10:00:00Z"));
        assertThat(inicioDaJanela("2026-08-16T10:59:59Z")).isEqualTo(OffsetDateTime.parse("2026-08-16T10:55:00Z"));
    }

    @Test
    void deveManterOInstanteQuandoEleJaEstaNoLimiteDaJanela() {
        assertThat(inicioDaJanela("2026-08-16T10:00:00Z")).isEqualTo(OffsetDateTime.parse("2026-08-16T10:00:00Z"));
        assertThat(inicioDaJanela("2026-08-16T10:10:00Z")).isEqualTo(OffsetDateTime.parse("2026-08-16T10:10:00Z"));
    }

    @Test
    void deveAlinharPorInstanteEnaoPorOffsetDoTexto() {
        OffsetDateTime emOutroFuso = OffsetDateTime.parse("2026-08-16T07:07:33-03:00");

        assertThat(janelaService.inicioDaJanela(emOutroFuso, CINCO_MINUTOS))
                .as("10:07:33Z escrito como 07:07:33-03:00 é o mesmo instante e cai na mesma janela")
                .isEqualTo(OffsetDateTime.parse("2026-08-16T10:05:00Z"));
    }

    @Test
    void oAlinhamentoNaoDependeDaHoraEmQueOProcessoSubiu() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-08-16T10:07:33Z");

        OffsetDateTime primeiraApuracao = janelaService.inicioDaJanela(instante, CINCO_MINUTOS);
        OffsetDateTime segundaApuracao = janelaService.inicioDaJanela(instante, CINCO_MINUTOS);

        assertThat(primeiraApuracao)
                .as("o cálculo é função pura do instante; não há memória de quando o painel iniciou")
                .isEqualTo(segundaApuracao)
                .isEqualTo(OffsetDateTime.parse("2026-08-16T10:05:00Z"));
    }

    private OffsetDateTime inicioDaJanela(String instante) {
        return janelaService.inicioDaJanela(OffsetDateTime.parse(instante), CINCO_MINUTOS);
    }
}
