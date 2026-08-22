package br.pucminas.aed.vendaingressospainel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.groups.Tuple.tuple;

import br.pucminas.aed.vendaingressospainel.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressospainel.domain.JanelaVendasVO;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PainelVendasServiceTest {

    private static final Duration DURACAO_DA_JANELA = Duration.ofMinutes(5);
    private static final Duration TOLERANCIA_DE_ATRASO = Duration.ofMinutes(2);
    private static final String SHOW = "evento-comercial-001";

    @Test
    void deveSepararEmJanelasPelaHoraDeOcorrenciaEnaoPelaDeChegada() {
        PainelVendasService painel = novoPainel();

        painel.registrar(evento("evento-001", "2026-08-16T10:04:59Z", SHOW));
        painel.registrar(evento("evento-002", "2026-08-16T10:05:00Z", SHOW));

        assertThat(painel.janelas())
                .as("os dois foram registrados no mesmo instante de processamento; só o ocorridoEm os separa")
                .extracting(JanelaVendasVO::getInicio, JanelaVendasVO::getIngressosEmitidos)
                .containsExactly(
                        tuple(OffsetDateTime.parse("2026-08-16T10:00:00Z"), 1L),
                        tuple(OffsetDateTime.parse("2026-08-16T10:05:00Z"), 1L)
                );
    }

    @Test
    void deveSepararAContagemPorEventoComercial() {
        PainelVendasService painel = novoPainel();

        painel.registrar(evento("evento-001", "2026-08-16T10:01:00Z", SHOW));
        painel.registrar(evento("evento-002", "2026-08-16T10:02:00Z", SHOW));
        painel.registrar(evento("evento-003", "2026-08-16T10:03:00Z", "evento-comercial-002"));

        assertThat(painel.janelas())
                .extracting(JanelaVendasVO::getEventoComercialId, JanelaVendasVO::getIngressosEmitidos)
                .containsExactlyInAnyOrder(
                        tuple(SHOW, 2L),
                        tuple("evento-comercial-002", 1L)
                );
    }

    @Test
    void deveContarUmaVezOMesmoEventoEntregueTresVezes() {
        PainelVendasService painel = novoPainel();
        IngressoEmitidoEvent evento = evento("evento-001", "2026-08-16T10:01:00Z", SHOW);

        painel.registrar(evento);
        painel.registrar(evento);
        painel.registrar(evento);

        assertThat(painel.janelas())
                .as("contar não é idempotente por natureza; a deduplicação é por eventoId")
                .singleElement()
                .extracting(JanelaVendasVO::getIngressosEmitidos)
                .isEqualTo(1L);
    }

    @Test
    void deveDeduplicarPorEventoIdEnaoPorIdentidadeDoNegocio() {
        PainelVendasService painel = novoPainel();

        painel.registrar(evento("evento-001", "2026-08-16T10:01:00Z", SHOW));
        painel.registrar(evento("evento-002", "2026-08-16T10:01:00Z", SHOW));

        assertThat(painel.janelas())
                .as("dois fatos distintos do mesmo show na mesma janela contam duas vezes")
                .singleElement()
                .extracting(JanelaVendasVO::getIngressosEmitidos)
                .isEqualTo(2L);
    }

    @Test
    void deveFecharSomenteAsJanelasVencidasAlemDaTolerancia() {
        PainelVendasService painel = novoPainel();
        painel.registrar(evento("evento-001", "2026-08-16T10:01:00Z", SHOW));
        painel.registrar(evento("evento-002", "2026-08-16T10:06:00Z", SHOW));

        painel.fecharJanelasVencidas(OffsetDateTime.parse("2026-08-16T10:07:30Z"));

        assertThat(painel.janelas())
                .as("a janela 10:00-10:05 vence às 10:07 com a tolerância; a de 10:05 ainda não fechou")
                .extracting(JanelaVendasVO::getInicio, JanelaVendasVO::isFechada)
                .containsExactly(
                        tuple(OffsetDateTime.parse("2026-08-16T10:00:00Z"), true),
                        tuple(OffsetDateTime.parse("2026-08-16T10:05:00Z"), false)
                );
    }

    @Test
    void deveContarORetardatarioNaJanelaDeleMesmoDepoisDoFechamento() {
        PainelVendasService painel = novoPainel();
        painel.registrar(evento("evento-001", "2026-08-16T10:01:00Z", SHOW));
        painel.fecharJanelasVencidas(OffsetDateTime.parse("2026-08-16T10:07:30Z"));

        painel.registrar(evento("evento-002", "2026-08-16T10:02:00Z", SHOW));

        assertThat(painel.janelas())
                .as("fechar publica o número; não descarta o retardatário")
                .singleElement()
                .extracting(JanelaVendasVO::getInicio, JanelaVendasVO::getIngressosEmitidos)
                .containsExactly(OffsetDateTime.parse("2026-08-16T10:00:00Z"), 2L);
    }

    @Test
    void deveProduzirOMesmoResultadoAoReprocessarEmOutraOrdem() {
        List<IngressoEmitidoEvent> fluxo = List.of(
                evento("evento-001", "2026-08-16T10:01:00Z", SHOW),
                evento("evento-002", "2026-08-16T10:04:59Z", SHOW),
                evento("evento-003", "2026-08-16T10:06:00Z", SHOW),
                evento("evento-004", "2026-08-16T10:06:30Z", "evento-comercial-002")
        );

        PainelVendasService primeiraApuracao = novoPainel();
        fluxo.forEach(primeiraApuracao::registrar);

        List<IngressoEmitidoEvent> reprocessamento = new ArrayList<>(fluxo);
        Collections.reverse(reprocessamento);
        PainelVendasService segundaApuracao = novoPainel();
        reprocessamento.forEach(segundaApuracao::registrar);
        reprocessamento.forEach(segundaApuracao::registrar);

        assertThat(descrever(segundaApuracao))
                .as("a janela é função pura do ocorridoEm e a contagem é deduplicada: reprocessar dá o mesmo número")
                .isEqualTo(descrever(primeiraApuracao));
    }

    @Test
    void deveRecusarEventoSemOcorridoEm() {
        PainelVendasService painel = novoPainel();

        Throwable erro = catchThrowable(
                () -> painel.registrar(new IngressoEmitidoEvent("evento-001", null, SHOW)));

        assertThat(erro)
                .as("ocorridoEm é o relógio da janela; sem ele o evento não é agregável e a falha é explícita")
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PainelVendasService novoPainel() {
        return new PainelVendasService(
                new RelogioOcorrenciaService(),
                new JanelaService(),
                DURACAO_DA_JANELA,
                TOLERANCIA_DE_ATRASO
        );
    }

    private List<String> descrever(PainelVendasService painel) {
        return painel.janelas().stream()
                .map(janela -> janela.getEventoComercialId()
                        + "|" + janela.getInicio()
                        + "|" + janela.getIngressosEmitidos())
                .toList();
    }

    private IngressoEmitidoEvent evento(String eventoId, String ocorridoEm, String eventoComercialId) {
        return new IngressoEmitidoEvent(eventoId, OffsetDateTime.parse(ocorridoEm), eventoComercialId);
    }
}
