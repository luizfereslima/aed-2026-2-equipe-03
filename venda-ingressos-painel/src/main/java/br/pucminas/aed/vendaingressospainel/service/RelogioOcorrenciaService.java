package br.pucminas.aed.vendaingressospainel.service;

import br.pucminas.aed.vendaingressospainel.domain.IngressoEmitidoEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * O relógio da agregação: o instante em que o fato ocorreu no domínio, lido de dentro do
 * evento ({@code ocorridoEm}).
 *
 * <p>A alternativa recusada foi o relógio de chegada — a hora do próprio painel no momento
 * em que a mensagem é consumida. Ele é mais simples porque está sempre à mão, mas a
 * pergunta que esta agregação responde é do negócio: "quantos ingressos saíram deste show
 * entre 20:00 e 20:05". Essa frase é sobre o show, não sobre o consumidor. Com o relógio de
 * chegada, um reprocessamento do tópico amanhã jogaria o histórico inteiro nas janelas de
 * amanhã. Com o relógio de ocorrência, o resultado é o mesmo em qualquer reprocessamento.
 *
 * <p>Decisão registrada em {@code docs/adr/ADR-003-agregacao-por-janela.md}.
 */
@Service
public class RelogioOcorrenciaService {

    public OffsetDateTime instanteDoFato(IngressoEmitidoEvent evento) {
        if (evento.getOcorridoEm() == null) {
            throw new IllegalArgumentException(
                    "ocorridoEm é obrigatório no contrato e é o relógio da janela; evento sem ele não é agregável"
            );
        }
        return evento.getOcorridoEm().withOffsetSameInstant(ZoneOffset.UTC);
    }
}
