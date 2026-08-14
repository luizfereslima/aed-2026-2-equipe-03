package br.pucminas.aed.vendaingressosconsumer.service;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;

@Entity
public class IngressoEmitidoVO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventoId;
    private String ingressoId;
    private String vendaId;
    private String eventoComercialId;
    private OffsetDateTime emitidoEm;

    protected IngressoEmitidoVO() {
    }

    public IngressoEmitidoVO(
            String eventoId,
            String ingressoId,
            String vendaId,
            String eventoComercialId,
            OffsetDateTime emitidoEm
    ) {
        this.eventoId = eventoId;
        this.ingressoId = ingressoId;
        this.vendaId = vendaId;
        this.eventoComercialId = eventoComercialId;
        this.emitidoEm = emitidoEm;
    }

    public Long getId() {
        return id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getIngressoId() {
        return ingressoId;
    }

    public String getVendaId() {
        return vendaId;
    }

    public String getEventoComercialId() {
        return eventoComercialId;
    }

    public OffsetDateTime getEmitidoEm() {
        return emitidoEm;
    }
}
