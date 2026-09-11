# Arquitetura do sistema

## 1. Visão e fronteiras

O publisher recebe a compra e publica fatos. O consumer mantém projeção/auditoria relacional.
O painel mantém agregação operacional em janelas de cinco minutos. Kafka é o meio de
comunicação; os três projetos Maven permanecem independentes.

## 2. Contratos

`IngressoEmitidoEvent` representa emissão concluída. `IngressoInvalidadoEvent` representa a
compensação concluída. Ambos usam CloudEvents 1.0, `eventoId` próprio, datas ISO-8601 e tipos
versionados documentados em [contrato.md](contrato.md). Consumidores ignoram campos desconhecidos.

## 3. Tópicos e grupos

```text
Publisher -> ingressos.ingresso-emitido.v1 -> grupo consumer
                                           -> grupo painel
Publisher -> ingressos.ingresso-invalidado.v1 -> grupo consumer
```

O primeiro tópico usa `eventoComercialId` como chave para preservar ordem por evento comercial.
Os tópicos têm três partições. Cada consumidor possui DLT própria.

## 4. Decisões registradas

- ADR-002: domínio, fronteiras e identidade dos eventos.
- ADR-003: agregação por hora de ocorrência.
- ADR-004: backpressure, retry, DLT e partições.
- ADR-006: Saga coreografada e compensação semântica.

## 5. Comportamento normal e de falha

Compra autorizada publica emissão. Falha transitória no consumo usa retry exponencial limitado
a 30 segundos. Falha de desserialização ou contrato inválido vai para DLT com headers de
diagnóstico. Invalidação publica novo fato; consumer atualiza a projeção para `INVALIDADO`.

## 6. Particionamento e ordenação

Mensagens do mesmo evento comercial preservam ordem dentro da partição. Não existe ordem total
entre eventos comerciais. A chave de invalidação também é `eventoComercialId`.

## 7. Observabilidade

O publisher registra publicação e falha. O consumer expõe `GET /ingressos/{ingressoId}`.
O painel expõe `GET /painel-vendas/janelas` e registra fechamento/correção de janelas. DLTs
separam falhas do consumer e do painel. Reprocessamento é manual e deve preservar o `eventoId`.

## 8. Limitações e custo das escolhas

O painel perde estado ao reiniciar e reconstrói pelo tópico. DLT e deduplicação ainda precisam
de política de retenção. O publisher tem dual-write e gateway simulado; outbox, estorno real,
liberação real de assento e observabilidade externa ficaram fora por não haver requisito e
infraestrutura suficientes nesta etapa.
