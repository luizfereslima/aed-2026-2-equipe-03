package br.pucminas.aed.vendaingressos.controller;

public class SolicitarInvalidacaoVO {
    private String vendaId;
    private String eventoComercialId;
    private String motivo;

    public String getVendaId() { return vendaId; }
    public void setVendaId(String vendaId) { this.vendaId = vendaId; }
    public String getEventoComercialId() { return eventoComercialId; }
    public void setEventoComercialId(String eventoComercialId) { this.eventoComercialId = eventoComercialId; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
