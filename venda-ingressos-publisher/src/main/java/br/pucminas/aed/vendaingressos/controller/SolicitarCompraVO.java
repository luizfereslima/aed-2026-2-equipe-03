package br.pucminas.aed.vendaingressos.controller;

public class SolicitarCompraVO {

    private String eventoComercialId;
    private String setorId;
    private String assentoId;

    public String getEventoComercialId() {
        return eventoComercialId;
    }

    public void setEventoComercialId(String eventoComercialId) {
        this.eventoComercialId = eventoComercialId;
    }

    public String getSetorId() {
        return setorId;
    }

    public void setSetorId(String setorId) {
        this.setorId = setorId;
    }

    public String getAssentoId() {
        return assentoId;
    }

    public void setAssentoId(String assentoId) {
        this.assentoId = assentoId;
    }
}
