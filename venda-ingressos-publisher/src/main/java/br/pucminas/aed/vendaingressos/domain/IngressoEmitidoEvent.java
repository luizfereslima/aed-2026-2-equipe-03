package br.pucminas.aed.vendaingressos.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class IngressoEmitidoEvent {

    private final String eventoId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final OffsetDateTime ocorridoEm;

    private final String ingressoId;
    private final String vendaId;
    private final String eventoComercialId;
    private final String setorId;
    private final String assentoId;

    @JsonCreator
    public IngressoEmitidoEvent(
            @JsonProperty("eventoId") String eventoId,
            @JsonProperty("ocorridoEm") OffsetDateTime ocorridoEm,
            @JsonProperty("ingressoId") String ingressoId,
            @JsonProperty("vendaId") String vendaId,
            @JsonProperty("eventoComercialId") String eventoComercialId,
            @JsonProperty("setorId") String setorId,
            @JsonProperty("assentoId") String assentoId
    ) {
        this.eventoId = eventoId;
        this.ocorridoEm = ocorridoEm;
        this.ingressoId = ingressoId;
        this.vendaId = vendaId;
        this.eventoComercialId = eventoComercialId;
        this.setorId = setorId;
        this.assentoId = assentoId;
    }

    public String getEventoId() {
        return eventoId;
    }

    public OffsetDateTime getOcorridoEm() {
        return ocorridoEm;
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

    public String getSetorId() {
        return setorId;
    }

    public String getAssentoId() {
        return assentoId;
    }
}
