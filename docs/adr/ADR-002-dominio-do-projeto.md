# ADR-002 — Domínio do projeto

## Status

Aceita · 2026-08-16 · Equipe 03

## Contexto

A disciplina Arquitetura Reativa e Event-Driven exige um domínio incremental que permita discutir comunicação orientada a eventos, idempotência, consistência eventual, compensação, CQRS e Event Sourcing em aulas futuras.

O domínio escolhido é venda de ingressos para eventos. O processo envolve solicitação de compra, reserva temporária de assento, autorização de pagamento em gateway externo, confirmação de venda e emissão de ingresso.

TODO_EQUIPE: informar qual integrante trouxe o domínio e de qual experiência real esse processo vem.

O termo evento possui ambiguidade no domínio: evento comercial representa show, festival ou apresentação; evento de domínio representa fato ocorrido no sistema. Por isso, o código e a documentação usam `EventoComercial` para a apresentação e `IngressoEmitidoEvent` para o fato de domínio.

## Decisão

O projeto adotará o domínio de venda de ingressos para eventos.

Fluxo principal:

1. Cliente solicita a compra de ingresso para determinado evento comercial, setor e assento.
2. O publisher avalia se a solicitação contém os identificadores necessários para reserva.
3. O assento é tratado como reservado no recorte inicial da aula.
4. O pagamento é solicitado por meio de uma abstração de gateway externo chamada `PagamentoService`.
5. Após autorização simulada do pagamento, a venda é confirmada.
6. O ingresso é emitido.
7. O publisher publica `IngressoEmitidoEvent` no Kafka.
8. O consumer processa o evento de forma idempotente e registra projeção/auditoria de ingresso emitido.

Critérios do domínio:

- ponto de decisão com regra de negócio: disponibilidade/reserva do assento;
- sistema externo: gateway de pagamento;
- caminho de exceção com compensação: liberação de assento e possível estorno quando uma operação posterior falhar;
- algo que valha reprocessar: projeção/auditoria de ingressos emitidos e ocupação.

O evento principal da Aula 02 é `IngressoEmitidoEvent`, pois representa um fato concluído. O contrato publicado contém `eventoId`, `ocorridoEm`, `ingressoId`, `vendaId`, `eventoComercialId`, `setorId` e `assentoId`.

O prazo real de reserva do assento ainda não foi definido.

TODO_DOMINIO: definir prazo real da reserva do assento.

## Alternativas consideradas

TODO_EQUIPE: candidato 1 e motivo da recusa.

TODO_EQUIPE: candidato 2 e motivo da recusa.

Também foi considerada a criação de um módulo Maven compartilhado para o contrato do evento. A alternativa foi recusada porque o exercício exige publisher e consumer independentes, sem módulo compartilhado de contratos. Cada aplicação declara sua própria representação de `IngressoEmitidoEvent`.

## Consequências aceitas

A arquitetura aceita consistência eventual entre emissão do ingresso no publisher e projeção/auditoria no consumer.

Mensagens Kafka podem ser entregues mais de uma vez. Por isso, o consumer deve ser idempotente e deduplicar por `eventoId`, não por `ingressoId`, `vendaId` ou outro identificador de entidade.

O efeito de negócio do consumer e o registro do evento processado devem ocorrer no mesmo commit de banco. O ACK Kafka deve acontecer somente depois do retorno do service transacional.

O domínio exigirá complexidade adicional quando compensações forem implementadas, especialmente para liberar assento, cancelar reserva e solicitar estorno de pagamento após falhas posteriores.

Contratos de eventos precisarão de versionamento e evolução compatível. O `ce_type` adotado é `ingressos.ingresso.emitido.v1`.

Haverá necessidade futura de correlação entre operações, observabilidade distribuída e estratégia de retenção da tabela de deduplicação.

Impacto nas Aulas 04 e 05:

- Em Saga/compensação, será necessário representar passos e falhas com correlação entre venda, pagamento, reserva e ingresso.
- Em CQRS/Event Sourcing, a projeção de ingressos emitidos poderá ser reconstruída a partir dos eventos, mas a Aula 02 não implementa essas estratégias.
- Mitigações previstas: manter eventos com identidade própria, versionar contratos, preservar idempotência e registrar novas decisões arquiteturais por ADR.
