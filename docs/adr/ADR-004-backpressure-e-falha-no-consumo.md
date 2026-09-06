# ADR-004 — Backpressure, particionamento e política de falha no consumo

## Status

Proposta · 2026-09-06 · Equipe 03

> `TODO_EQUIPE:` mudar para *Aceita* quando a equipe validar a etapa.

## Contexto

A aula de Programação Reativa e Backpressure pergunta o que o sistema faz quando chega mais
trabalho do que ele consegue processar. Até aqui a resposta honesta era: **não estava
decidido**.

O estado que esta decisão encontrou:

- **Sem controle de vazão.** Tópico com uma partição (`KafkaConfig`), `concurrency` no
  padrão 1, `max.poll.records` no padrão 500. Não havia como absorver pico aumentando
  consumidor, porque partição única limita o grupo a um consumidor ativo.
- **Sem política de falha.** Os dois consumidores usavam `JsonDeserializer` direto, sem
  `ErrorHandlingDeserializer`, sem tratador de erro escolhido e sem tópico de descarte. O
  comportamento diante de uma carga malformada era o padrão do contêiner — que ninguém tinha
  escolhido nem testado.
- **Sem backpressure no produtor.** O `IngressoController` respondia `202 Accepted` antes de
  o broker confirmar qualquer coisa, e falha de publicação virava apenas linha de log.
- **Um defeito latente de idempotência.** `PainelVendasService.registrar` marcava o
  `eventoId` na memória de deduplicação **antes** de fazer o trabalho, que podia lançar em
  seguida. Um evento que falhasse ficaria registrado como apurado sem nunca ter sido contado,
  e a reentrega — que o pipeline *at-least-once* garante que vem — cairia no desvio de
  duplicata. O evento sumiria com uma linha de DEBUG.

O ADR-002 já tinha deixado marcada para esta etapa a medição do desequilíbrio entre
partições, que com uma partição só não existia para ser medida.

## Decisão

### 1. O backpressure é o próprio laço de consumo, tornado explícito

O consumidor Kafka é *pull-based*: o contêiner só busca o próximo lote quando o anterior
termina. A demanda parte de quem consome, e a fila fica no broker — que é feito para
guardá-la — e não em memória do processo. Esse mecanismo já existia; o que faltava era
**parametrizá-lo como decisão**, em vez de herdar defaults.

Foi por isso que **não** se trocou a stack por Reactor. O `ack-mode: manual` com confirmação
depois do retorno do service, somado a `max-poll-records`, é o sinal de demanda; acrescentar
`Flux` por cima disso adicionaria uma segunda camada de controle de fluxo sobre uma que já
funciona, contra a proibição de overengineering da constituição.

> `TODO_EQUIPE:` conferir contra o enunciado da aula. Se ele exigir nominalmente programação
> reativa, o menor movimento defensável é `reactor-kafka` **apenas no painel**, mantendo o
> `venda-ingressos-consumer` como está — e este ADR precisa ser revisto, não contornado.

### 2. Três partições no tópico

O contrato nunca prometeu ordem total, e já avisava que este número ia crescer. A chave de
partição continua sendo o `eventoComercialId`, então **a ordem por evento comercial continua
garantida** — cada partição segue consumida por uma thread só.

Passar de uma partição é também pré-requisito da medição prevista no ADR-002: com partição
única não há desequilíbrio a medir. A avaliação da chave composta (evento comercial + setor)
**continua parada**, aguardando a medição.

### 3. Paralelismo diferente em cada consumidor, pelo motivo de cada um

| Aplicação | `concurrency` | Por quê |
|---|---|---|
| `venda-ingressos-consumer` | 3 | O efeito é uma transação em banco por registro. Threads paralelas em partições distintas aumentam vazão de verdade. |
| `venda-ingressos-painel` | 1 | A apuração inteira é uma seção crítica única (`PainelVendasService` é `synchronized`, porque a contagem é estado global em memória). Consumidores paralelos só fariam fila no mesmo cadeado: gasto de thread sem ganho de vazão. |

Paralelizar o painel de verdade exigiria particionar a apuração por `eventoComercialId`.
Fica registrado como evolução possível, **não implementado**.

### 4. `max.poll.records` explícito, casado com `max.poll.interval.ms`

50 no consumer, 10 no painel. A regra que os dois respeitam: *registros por lote × custo por
registro* precisa ficar confortavelmente abaixo de `max.poll.interval.ms`, senão o broker
considera o consumidor travado e rebalanceia o grupo — expulsando-o justamente quando ele
está ocupado. É este o par de números que define a vazão máxima aceita.

### 5. Retry serve para falha transitória; carga inválida não fica válida por insistência

Dois caminhos, deliberadamente diferentes:

- **Transitória** (banco, rede, broker): espera dobrando a partir de 1 s, desistindo aos
  30 s. A espera é o que impede o retry de virar tempestade contra um recurso que já está
  sofrendo.
- **Não repetível** (falha de desserialização, já fatal por padrão, e
  `IllegalArgumentException` de contrato violado): vai direto ao tópico de descarte, sem
  queimar tentativa. Insistir dez vezes na mesma carga dá o mesmo resultado dez vezes.

Para que a falha de desserialização seja tratável em vez de estourar dentro do `poll` e
travar a partição, o `JsonDeserializer` passou a ser envolvido por `ErrorHandlingDeserializer`
nos dois consumidores.

### 6. Tópico de descarte por consumidor, não compartilhado

`ingressos.ingresso-emitido.v1.dlt-consumer` e `ingressos.ingresso-emitido.v1.dlt-painel`.

Os dois grupos falham por motivos diferentes sobre a mesma mensagem: uma carga sem
`ocorridoEm` é fatal para a janela e irrelevante para a projeção, que nem lê aquele campo
para decidir. Descarte compartilhado misturaria as duas histórias e tiraria de cada
consumidor o direito de ter a própria política.

O encaminhamento preserva o número da partição — e com ele o agrupamento por evento
comercial —, por isso cada tópico de descarte tem o mesmo número de partições da origem.

### 7. O backpressure do produtor chega ao cliente HTTP

O buffer do produtor é finito (32 MB) e a espera por espaço é limitada
(`max.block.ms: 5000`). Esgotados os dois, `send()` falha de forma síncrona, a
`PublicacaoIndisponivelException` sobe e o `IngressoController` responde **503 com
`Retry-After`**.

Responder 202 nesse caso seria mentira: o cliente ouviria "aceito" sobre um evento que não
entrou em lugar nenhum. A alternativa — enfileirar em memória — apenas converteria o
problema em consumo de heap.

Isto vale só para a falha **síncrona**. A falha **assíncrona**, no callback, continua sendo
registrada em log: ali a mensagem já foi aceita pelo produtor e a venda já foi confirmada, e
o dual-write assumido no ADR-002 já aconteceu. A correção definitiva é o outbox, previsto
para a etapa de Event Sourcing.

### 8. Ordem de idempotência corrigida no painel: validar, marcar, contar

A memória da deduplicação só pode registrar o que de fato foi contado. É o mesmo princípio
que o `venda-ingressos-consumer` obtém de graça com `@Transactional` — efeito de negócio e
memória da deduplicação no mesmo commit. No painel o estado é de memória e não há transação,
então a atomicidade vem da ordem, garantida pelo `synchronized`.

O `venda-ingressos-consumer` ganhou, pelo mesmo motivo, recusa explícita de evento sem
`eventoId`: sem chave de deduplicação não há como ser idempotente, e a falha precisa ser
visível em vez de virar erro de acesso a dados lá adiante.

## Alternativas consideradas

- **Trocar por Reactor / WebFlux / `reactor-kafka`.** Recusada por ora — ver decisão 1 e o
  `TODO_EQUIPE` que ela carrega.
- **Fila em memória no publisher para absorver pico.** Recusada. Move a fila para dentro do
  processo, onde ela não é durável nem observável, e troca uma recusa honesta por
  `OutOfMemoryError` mais tarde.
- **Pausar e retomar o contêiner do listener.** Recusada por desnecessária: o laço de poll já
  não busca lote novo antes de terminar o anterior. Seria a ferramenta certa para reagir a um
  sinal externo — um recurso a jusante em sobrecarga —, o que não existe aqui.
- **Retry infinito.** Recusada. Trava a partição para sempre na primeira carga inválida — o
  problema que esta etapa veio resolver.
- **Descartar em silêncio o que falha.** Recusada. Contraria a constituição e transforma
  defeito de contrato em número errado sem rastro.
- **Tópico de descarte único para os dois consumidores.** Recusada — ver decisão 6.
- **Aumentar `concurrency` também no painel.** Recusada — ver decisão 3.

## Consequências aceitas

**O tópico de descarte não tem consumidor.** Ele é um estacionamento: a inspeção e o
reprocessamento são manuais, pelo procedimento do README. Automatizar reprocessamento exigiria
decidir antes o que é seguro reprocessar, e essa decisão não está tomada.

**O descarte cresce sem plano de expurgo**, como as memórias de deduplicação do consumer e do
painel. A política de retenção dos três sai junto, quando ficar claro por quanto tempo uma
reentrega ainda é plausível.

**A carga que falhou na desserialização chega ao descarte em base64.** O serializador do
produtor de descarte é o `JsonSerializer`, e os bytes crus que não viraram objeto são
escritos como texto. Perde-se legibilidade imediata; ganha-se um caminho só, sem um segundo
serializador para manter. Os cabeçalhos de diagnóstico que o Spring acrescenta continuam
legíveis, e são eles que dizem o que aconteceu.

**Três partições tornam visível a ausência de ordem total.** Já era o que o contrato dizia,
mas até agora a prática desmentia o documento em favor da garantia mais forte. Quem tiver
assumido ordem total sem ler o contrato vai descobrir agora.

**Aumentar a partição de um tópico existente não move o que já está lá.** As mensagens
antigas continuam na partição 0. A distribuição só passa a valer para o que for publicado
depois.

**O `atraso-simulado` é andaime de demonstração dentro do código de produção.** Vem desligado
(`PT0S`) e não há caminho em que ele ligue sem alguém escrever o valor na configuração. Sem
ele, porém, o painel é rápido demais para que a fila chegue a existir, e não haveria o que
demonstrar.

**O painel continua com um gargalo de projeto**, não de configuração: a seção crítica única.
Sob carga sustentada ele vai acumular lag mesmo com o freio desligado. É verdade nova e
visível, não verdade criada por esta etapa.

**O encaminhamento ao tópico de descarte não tem teste automatizado.** É o único
comportamento desta etapa verificado apenas à mão, pelo procedimento do README. Um teste com
Kafka embarcado é o lugar certo para cobri-lo, e está previsto junto com o teste de contrato
ponta a ponta.
