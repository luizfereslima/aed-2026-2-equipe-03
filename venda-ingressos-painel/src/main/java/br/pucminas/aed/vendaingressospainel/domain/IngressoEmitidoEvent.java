package br.pucminas.aed.vendaingressospainel.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Representação local do contrato {@code ingressos.ingresso.emitido.v1}.
 *
 * <p>O painel declara apenas os três campos de que precisa e ignora os demais. Não é
 * descuido: é a regra de compatibilidade FULL do {@code docs/contrato.md} exercida na
 * prática — o consumidor não precisa conhecer campo que não usa, e por isso o publisher
 * pode ganhar campos novos sem que este serviço seja implantado antes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngressoEmitidoEvent {

    private final String eventoId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final OffsetDateTime ocorridoEm;

    private final String eventoComercialId;

    @JsonCreator
    public IngressoEmitidoEvent(
            @JsonProperty("eventoId") String eventoId,
            @JsonProperty("ocorridoEm") OffsetDateTime ocorridoEm,
            @JsonProperty("eventoComercialId") String eventoComercialId
    ) {
        this.eventoId = eventoId;
        this.ocorridoEm = ocorridoEm;
        this.eventoComercialId = eventoComercialId;
    }

    public String getEventoId() {
        return eventoId;
    }

    public OffsetDateTime getOcorridoEm() {
        return ocorridoEm;
    }

    public String getEventoComercialId() {
        return eventoComercialId;
    }
}
