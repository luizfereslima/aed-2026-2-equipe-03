package br.pucminas.aed.vendaingressospainel.domain;

import java.time.OffsetDateTime;

/**
 * Resultado apurado de uma janela: quantos ingressos foram emitidos para um evento
 * comercial no intervalo {@code [inicio, fim)}, medido pela hora de ocorrência do fato.
 */
public class JanelaVendasVO {

    private final String eventoComercialId;
    private final OffsetDateTime inicio;
    private final OffsetDateTime fim;
    private final long ingressosEmitidos;
    private final boolean fechada;

    public JanelaVendasVO(
            String eventoComercialId,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            long ingressosEmitidos,
            boolean fechada
    ) {
        this.eventoComercialId = eventoComercialId;
        this.inicio = inicio;
        this.fim = fim;
        this.ingressosEmitidos = ingressosEmitidos;
        this.fechada = fechada;
    }

    public String getEventoComercialId() {
        return eventoComercialId;
    }

    public OffsetDateTime getInicio() {
        return inicio;
    }

    public OffsetDateTime getFim() {
        return fim;
    }

    public long getIngressosEmitidos() {
        return ingressosEmitidos;
    }

    public boolean isFechada() {
        return fechada;
    }
}
