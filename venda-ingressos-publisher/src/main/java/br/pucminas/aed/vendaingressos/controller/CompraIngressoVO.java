package br.pucminas.aed.vendaingressos.controller;

public class CompraIngressoVO {

    private final String vendaId;
    private final String ingressoId;
    private final String situacao;

    public CompraIngressoVO(String vendaId, String ingressoId, String situacao) {
        this.vendaId = vendaId;
        this.ingressoId = ingressoId;
        this.situacao = situacao;
    }

    public String getVendaId() {
        return vendaId;
    }

    public String getIngressoId() {
        return ingressoId;
    }

    public String getSituacao() {
        return situacao;
    }
}
