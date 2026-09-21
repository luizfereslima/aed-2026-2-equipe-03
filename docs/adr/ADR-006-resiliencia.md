# ADR-006 — Resiliência, reprocessamento e Saga coreografada

## Status

Aceita · 2026-09-21 · Equipe 03

## Contexto

O fluxo de venda publica `IngressoEmitidoEvent` e possui dois consumidores independentes. Uma
mensagem pode falhar por indisponibilidade transitória do banco, por carga inválida ou por uma
regra de contrato ausente. Sem uma decisão explícita, a primeira falha poderia travar a partição,
ser descartada silenciosamente ou ser repetida para sempre. O sistema também precisa desfazer uma
emissão já projetada quando o ingresso for invalidado, sem apagar o fato original.

Este ADR complementa a decisão de Saga registrada em
`ADR-006-saga-de-compensacao.md` e fixa o caminho final exigido para operação e manutenção.

## Decisão

### 1. Classificação e retentativa

Falhas transitórias são indisponibilidade temporária de banco, lock/deadlock, timeout de rede,
erro temporário do broker e respostas HTTP 503/429 de uma dependência. Elas usam quatro retries,
com espera exponencial de 1 s, 2 s, 4 s e 8 s. Depois da quarta nova tentativa, a mensagem é
encaminhada à DLT. O limite evita prender uma partição indefinidamente e dá tempo curto para um
recurso voltar.

Falhas permanentes são JSON que não desserializa, ausência de campo necessário e violação de
contrato/regra de negócio. `ErrorHandlingDeserializer` entrega o erro ao container; o
`DefaultErrorHandler` não retenta a desserialização e também não retenta `IllegalArgumentException`.
O evento segue diretamente para a DLT própria do consumidor.

### 2. DLT, diagnóstico e reprocessamento

O consumer usa `ingressos.ingresso-emitido.v1.dlt-consumer` e
`ingressos.ingresso-invalidado.v1.dlt-consumer`; o painel usa
`ingressos.ingresso-emitido.v1.dlt-painel`. O encaminhamento preserva o payload original, a
partição e todos os cabeçalhos CloudEvents, inclusive `ce_id`. O Spring acrescenta cabeçalhos
com exceção, mensagem, tópico, partição e offset de origem.

Não existe reprocessamento automático. Uma pessoa inspeciona a DLT, corrige a causa e chama
`POST /operacoes/dlq/reprocessamentos` no consumer com tópico, partição e offset. O endpoint lê
uma mensagem específica com consumidor temporário, valida os cabeçalhos originais e republica os
mesmos bytes no tópico original, preservando `ce_id`. Repetir a chamada é seguro para o efeito de
projeção porque a deduplicação usa a identidade do fato.

A DLT é acompanhada pelo time em cada operação e deve ser consultada quando a taxa de entrada
subir; não é depósito sem dono nem fila de retry automático.

### 3. Saga de compensação

Adota-se coreografia. O publisher detecta a invalidação e publica o fato novo
`IngressoInvalidadoEvent` em `ingressos.ingresso-invalidado.v1`. O consumer consome esse fato,
marca a projeção como `INVALIDADO` e registra o novo `eventoId` na mesma transação. O estado fica
observável em `GET /ingressos/{ingressoId}`. Não há chamada REST entre publisher e consumer,
`UPDATE` corretivo do passado nem `DELETE` do ingresso.

Coreografia foi escolhida porque os participantes já se comunicam por fatos e o caminho tem dois
efeitos independentes. Orquestração traria um coordenador e estado adicional para uma compensação
simples, mas seria preferível se surgissem muitos passos, prazos e decisões centralizadas.

### 4. Falha da própria compensação

Uma falha transitória ao processar `IngressoInvalidadoEvent` segue a mesma política de quatro
retries. Se esgotar as tentativas, o evento permanece na DLT do consumer, com o estado da projeção
ainda `EMITIDO`. Essa é uma situação indeterminada, não uma compensação silenciosa: a operação
inspeciona a DLT, corrige a causa e reprocessa o fato. O consumer deduplica por `eventoId`, então
invalidar duas vezes não devolve/libera o efeito duas vezes. Não existe "compensação da
compensação" recursiva nem alteração manual sem fato registrado.

## Alternativas recusadas

- Retry infinito: travaria o offset e esconderia as mensagens posteriores da partição.
- DLT única: misturaria falhas e políticas de dois consumidores independentes.
- UPDATE/DELETE no banco: apagaria a trilha append-only e impediria auditoria.
- Rollback distribuído ou try/catch com chamadas REST: criaria acoplamento temporal e não
  desfaria um evento já publicado.
- Reprocessamento automático em laço: devolveria a mesma carga inválida à DLT sem corrigir a causa.
- Kafka Streams, outbox e observabilidade distribuída: ficam fora por não serem necessários para
  demonstrar este recorte; são evoluções condicionadas a requisitos futuros.

## Consequências aceitas

- O caminho é eventualmente consistente: por alguns instantes a projeção pode estar `EMITIDO`
  depois de a invalidação já ter sido publicada.
- A DLT exige operação humana, retenção e monitoramento; sem dono ela vira perda de dados com nome.
- Quatro retries seguram a partição durante até 15 s de espera cumulativa, sacrificando vazão
  imediata para dar chance a uma indisponibilidade curta.
- O endpoint de reprocessamento é deliberadamente operacional e sem automação de seleção; ele
  depende de autenticação de infraestrutura quando exposto fora do ambiente acadêmico.
- Payloads malformados preservam bytes e podem aparecer codificados pela estratégia de serialização
  da DLT, o que reduz legibilidade imediata.
- O publisher ainda aceita a consequência de dual-write entre a venda simulada e Kafka; outbox
  continua fora do escopo.
- A chave `eventoComercialId` concentra picos de um único show em uma partição; mais instâncias
  não vencem esse teto sem mudar a garantia de ordenação.
