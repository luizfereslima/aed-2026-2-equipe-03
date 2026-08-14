package br.pucminas.aed.vendaingressosconsumer.service;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;

@Entity
public class EventoProcessadoVO {

    @Id
    private String eventoId;

    private OffsetDateTime processadoEm;

    protected EventoProcessadoVO() {
    }

    public EventoProcessadoVO(String eventoId, OffsetDateTime processadoEm) {
        this.eventoId = eventoId;
        this.processadoEm = processadoEm;
    }

    public String getEventoId() {
        return eventoId;
    }

    public OffsetDateTime getProcessadoEm() {
        return processadoEm;
    }
}
