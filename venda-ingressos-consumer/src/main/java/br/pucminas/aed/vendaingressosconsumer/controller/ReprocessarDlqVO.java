package br.pucminas.aed.vendaingressosconsumer.controller;

public record ReprocessarDlqVO(String topico, int particao, long offset) {
}
