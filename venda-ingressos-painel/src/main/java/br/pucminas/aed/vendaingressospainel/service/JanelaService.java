package br.pucminas.aed.vendaingressospainel.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Alinhamento da janela pelo relógio, e não pela hora em que o processo subiu.
 *
 * <p>O início é o instante truncado ao múltiplo anterior da duração da janela contado a
 * partir da época em UTC. Com janela de cinco minutos, os limites caem sempre em
 * {@code :00, :05, :10, ...} — reiniciar o painel às 10:03 não desloca nada.
 */
@Service
public class JanelaService {

    public OffsetDateTime inicioDaJanela(OffsetDateTime instante, Duration duracaoDaJanela) {
        if (duracaoDaJanela.isZero() || duracaoDaJanela.isNegative()) {
            throw new IllegalArgumentException("a duração da janela deve ser positiva");
        }
        long segundosDaJanela = duracaoDaJanela.getSeconds();
        long segundos = instante.toEpochSecond();
        long inicio = Math.floorDiv(segundos, segundosDaJanela) * segundosDaJanela;
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(inicio), ZoneOffset.UTC);
    }
}
