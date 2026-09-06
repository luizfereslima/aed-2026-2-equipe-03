package br.pucminas.aed.vendaingressos.service;

/**
 * O evento não pôde nem ser aceito para publicação, porque o produtor Kafka ficou sem espaço.
 *
 * <p>Distingue-se da falha assíncrona de publicação, em que a mensagem já foi aceita pelo
 * produtor e a venda já foi confirmada. Aqui nada foi aceito, então a solicitação é recusada.
 */
public class PublicacaoIndisponivelException extends RuntimeException {

    public PublicacaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
