package br.pucminas.aed.vendaingressosconsumer.service;

public record ReprocessamentoDlqResultado(
        String topicoOriginal,
        int particaoOriginal,
        long offsetOriginal,
        String ceId
) {
}
