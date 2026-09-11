# ADR-006 — Saga coreografada para invalidação de ingresso

## Status

Aceita · 2026-09-11 · Equipe 03

## Contexto

A emissão confirma reserva, pagamento e ingresso em momentos diferentes. Depois da emissão,
cancelamento ou recusa de uma etapa posterior não pode apagar o fato já publicado. A projeção
precisa mostrar o estado intermediário `EMITIDO` e o estado compensado `INVALIDADO`.

## Decisão

Adotar Saga coreografada. O publisher publica `IngressoInvalidadoEvent` no tópico
`ingressos.ingresso-invalidado.v1` quando uma invalidação é solicitada. O consumer recebe o
evento, localiza o ingresso por `ingressoId`, altera sua projeção para `INVALIDADO` e registra
o `eventoId` da compensação na mesma transação. O estado fica observável em
`GET /ingressos/{ingressoId}`.

Eventos de emissão e compensação possuem identidades próprias. Repetir a compensação com o
mesmo `eventoId` não repete o efeito. A compensação é fato de domínio, não rollback de banco
nem chamada síncrona encadeada.

## Alternativas recusadas

- **Rollback distribuído:** recusado; não desfaz publicação Kafka nem o ingresso já comunicado.
- **Orquestrador central:** recusado nesta etapa; adicionaria um coordenador e estado de
  processo sem necessidade demonstrada para dois fatos e dois participantes.
- **DELETE do ingresso:** recusado; destruiria a trilha de auditoria e impediria distinguir
  emissão de invalidação.

## Consequências aceitas

- Existe consistência eventual: durante a compensação o ingresso pode permanecer `EMITIDO`.
- Se a compensação falhar, o retry/DLT do consumer preserva o evento para reprocessamento;
  até lá, a projeção continua no estado anterior.
- O publisher ainda possui dual-write entre estado de venda simulado e Kafka; outbox fica
  adiado para a etapa de persistência/event sourcing.
- A reserva e o estorno reais ainda são simulados; regras de prazo e política financeira ficam
  como `TODO_DOMINIO:` até decisão da equipe.

## Observabilidade e reprocessamento

O estado é consultável por HTTP, o evento possui CloudEvents 1.0 e o consumer deduplica por
`eventoId`. Reprocessar a mesma compensação não muda o resultado depois da primeira aplicação.
