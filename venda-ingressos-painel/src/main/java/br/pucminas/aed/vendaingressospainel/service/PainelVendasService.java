package br.pucminas.aed.vendaingressospainel.service;

import br.pucminas.aed.vendaingressospainel.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressospainel.domain.JanelaVendasVO;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Responde à pergunta do organizador: quantos ingressos foram emitidos para cada evento
 * comercial em cada janela de cinco minutos.
 *
 * <p>A janela de um evento é função pura do {@code ocorridoEm} dele. Um retardatário sempre
 * cai na janela a que pertence, mesmo que ela já tenha sido fechada e logada — nesse caso o
 * painel loga a correção e passa a expor o valor corrigido. Nada é descartado, e é por isso
 * que reprocessar o tópico produz exatamente o mesmo estado final.
 *
 * <p>Contar não é efeito naturalmente idempotente, então a deduplicação é por
 * {@code eventoId} — a identidade do fato — e nunca por {@code ingressoId} ou {@code vendaId}.
 */
@Service
public class PainelVendasService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PainelVendasService.class);

    private final RelogioOcorrenciaService relogioOcorrenciaService;
    private final JanelaService janelaService;
    private final Duration duracaoDaJanela;
    private final Duration toleranciaDeAtraso;

    private final Map<ChaveJanela, ApuracaoDaJanela> apuracoes = new ConcurrentHashMap<>();
    private final Set<String> eventosApurados = ConcurrentHashMap.newKeySet();

    public PainelVendasService(
            RelogioOcorrenciaService relogioOcorrenciaService,
            JanelaService janelaService,
            @Value("${app.painel.duracao-janela}") Duration duracaoDaJanela,
            @Value("${app.painel.tolerancia-atraso}") Duration toleranciaDeAtraso
    ) {
        this.relogioOcorrenciaService = relogioOcorrenciaService;
        this.janelaService = janelaService;
        this.duracaoDaJanela = duracaoDaJanela;
        this.toleranciaDeAtraso = toleranciaDeAtraso;
    }

    public synchronized void registrar(IngressoEmitidoEvent evento) {
        if (!eventosApurados.add(evento.getEventoId())) {
            LOGGER.debug("Evento já apurado, ignorado eventoId={}", evento.getEventoId());
            return;
        }

        OffsetDateTime ocorridoEm = relogioOcorrenciaService.instanteDoFato(evento);
        ChaveJanela chave = new ChaveJanela(
                evento.getEventoComercialId(),
                janelaService.inicioDaJanela(ocorridoEm, duracaoDaJanela)
        );

        ApuracaoDaJanela apuracao = apuracoes.computeIfAbsent(chave, ignorada -> new ApuracaoDaJanela());
        apuracao.ingressosEmitidos++;

        if (apuracao.fechada) {
            LOGGER.warn(
                    "JANELA CORRIGIDA eventoComercialId={} inicio={} fim={} ingressos={} retardatarioEventoId={}",
                    chave.eventoComercialId(),
                    chave.inicio(),
                    chave.inicio().plus(duracaoDaJanela),
                    apuracao.ingressosEmitidos,
                    evento.getEventoId()
            );
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void fecharJanelasVencidas() {
        fecharJanelasVencidas(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * A tolerância adia a divulgação do resultado; ela não fecha a porta para o retardatário.
     * Fechar significa "já dá para publicar este número", não "não aceito mais eventos".
     */
    synchronized void fecharJanelasVencidas(OffsetDateTime agora) {
        apuracoes.forEach((chave, apuracao) -> {
            if (apuracao.fechada) {
                return;
            }
            OffsetDateTime fim = chave.inicio().plus(duracaoDaJanela);
            if (fim.plus(toleranciaDeAtraso).isAfter(agora)) {
                return;
            }
            apuracao.fechada = true;
            LOGGER.info(
                    "JANELA FECHADA eventoComercialId={} inicio={} fim={} ingressos={}",
                    chave.eventoComercialId(),
                    chave.inicio(),
                    fim,
                    apuracao.ingressosEmitidos
            );
        });
    }

    public synchronized List<JanelaVendasVO> janelas() {
        List<JanelaVendasVO> resultado = new ArrayList<>();
        apuracoes.forEach((chave, apuracao) -> resultado.add(new JanelaVendasVO(
                chave.eventoComercialId(),
                chave.inicio(),
                chave.inicio().plus(duracaoDaJanela),
                apuracao.ingressosEmitidos,
                apuracao.fechada
        )));
        Comparator<JanelaVendasVO> porJanelaEDepoisPorEventoComercial = Comparator
                .comparing(JanelaVendasVO::getInicio)
                .thenComparing(JanelaVendasVO::getEventoComercialId);
        resultado.sort(porJanelaEDepoisPorEventoComercial);
        return resultado;
    }

    private record ChaveJanela(String eventoComercialId, OffsetDateTime inicio) {
    }

    private static final class ApuracaoDaJanela {
        private long ingressosEmitidos;
        private boolean fechada;
    }
}
