# Entrega — Aula 04

> Esta entrega foi escrita a partir do estado do repositório e do ADR-004, sem
> o enunciado oficial da aula à vista. Conferir contra o enunciado antes de entregar,
> especialmente as perguntas da seção final — a estrutura foi espelhada da Aula 03, e as
> perguntas reais podem ser outras.

## O que foi feito

O sistema passou a ter uma resposta escrita para a pergunta "o que acontece quando chega mais
trabalho do que se consegue processar" — e para a irmã dela, "o que acontece quando uma
mensagem não pode ser processada de jeito nenhum". Até esta etapa nenhuma das duas estava
decidida; valiam os padrões do contêiner, que ninguém tinha escolhido nem testado.

Em resumo:

- **Vazão explícita.** O tópico passou de uma para três partições, o
  `venda-ingressos-consumer` passou a consumir com três threads e cada consumidor ganhou um
  `max.poll.records` escolhido em função do custo do próprio trabalho.
- **Política de falha.** `ErrorHandlingDeserializer` nos dois consumidores, retry com espera
  crescente para falha transitória, e encaminhamento direto ao tópico de descarte para o que
  não fica válido por insistência. Cada consumidor tem o seu tópico de descarte.
- **Backpressure chegando ao cliente.** O buffer do produtor é finito e a espera por espaço é
  limitada; esgotados os dois, o publisher responde `503` com `Retry-After` em vez de `202`.
- **Duas correções de idempotência**, encontradas durante a etapa e descritas abaixo.

## Onde está cada artefato

- [ADR-004 — Backpressure, particionamento e política de falha no consumo](../adr/ADR-004-backpressure-e-falha-no-consumo.md)
- [Como demonstrar backpressure](../../README.md#como-demonstrar-backpressure) — no README
- [Como verificar o tópico de descarte](../../README.md#como-verificar-o-tópico-de-descarte) — no README
- [Registro de uso de IA](../IA.md)

## O que apareceu no caminho

Duas correções que não estavam previstas e que a etapa tornou visíveis. Nenhuma das duas é
mudança de contrato.

**O painel marcava antes de contar.** `PainelVendasService.registrar` inseria o `eventoId` no
conjunto de eventos apurados **antes** de calcular a janela, que podia lançar em seguida. Um
evento que falhasse ficava registrado como apurado sem nunca ter sido contado — e a
reentrega, que o pipeline *at-least-once* garante que vem, cairia no desvio de duplicata e
sumiria com uma linha de DEBUG. A ordem passou a ser **validar, marcar, contar**, que é o
mesmo princípio que o `venda-ingressos-consumer` obtém de graça com `@Transactional`: a
memória da deduplicação só registra o que de fato aconteceu.

Vale dizer o que essa correção **não** conserta: o teste que existia
(`deveRecusarEventoSemOcorridoEm`) validava apenas a primeira entrega, e por isso o defeito
passava despercebido. O teste novo
(`naoDeveMarcarComoApuradoUmEventoQueFalhouAntesDeSerContado`) é que fixa o comportamento na
reentrega.

**Nenhum dos dois consumidores exigia `eventoId`.** O contrato declara o campo obrigatório,
mas um JSON sem ele é sintaticamente válido, e sem `eventoId` não existe chave de
deduplicação — logo, não existe idempotência. No painel a falha aparecia como
`NullPointerException` vinda de dentro de um `ConcurrentHashMap`, num ponto que não dizia
nada sobre a causa; no consumer, como erro de acesso a dados mais adiante. Os dois passaram a
recusar explicitamente, com a exceção classificada como não repetível — a mensagem vai direto
ao tópico de descarte em vez de gastar 30 segundos de retry para acabar no mesmo lugar.

## As perguntas

> Conferir as perguntas contra o enunciado oficial antes do envio final.

### 1. Onde está o backpressure neste sistema

No próprio laço de consumo. O consumidor Kafka é *pull-based*: o contêiner só busca o próximo
lote depois que o anterior terminou, e o `ack-mode: manual` com confirmação após o retorno do
service garante que "terminou" quer dizer processado, não recebido. A demanda parte de quem
consome.

A consequência prática é que **a fila fica no broker**, que é feito para guardá-la, e não em
memória do processo consumidor nem no produtor. É o que a demonstração do README mostra: o
painel freado acumula lag no Kafka e drena sozinho, sem que ninguém precise avisar o produtor
para desacelerar, e sem que uma única mensagem se perca.

O mecanismo já existia — o que faltava era escolhê-lo como decisão e parametrizá-lo, em vez
de herdar os defaults. Foi por isso que a stack **não** foi trocada por Reactor: seria uma
segunda camada de controle de fluxo por cima de uma que já funciona.

### 2. O que limita a vazão, concretamente

O par `max.poll.records` × `max.poll.interval.ms`. Registros por lote vezes custo por
registro precisa ficar confortavelmente abaixo do intervalo, senão o broker considera o
consumidor travado e rebalanceia o grupo — expulsando-o justamente quando ele está ocupado.
São 50 registros no consumer e 10 no painel, pelo custo diferente de cada um.

O segundo limite é o paralelismo, e ele é diferente nos dois consumidores de propósito: três
threads no `venda-ingressos-consumer`, cuja unidade de trabalho é uma transação em banco por
registro, e **uma** no painel, cuja apuração inteira é uma seção crítica única. Aumentar o
paralelismo do painel só faria threads fazerem fila no mesmo cadeado.

### 3. O que acontece com uma mensagem que não pode ser processada

Depende de por que ela falhou, e a distinção é deliberada: **retry serve para falha
transitória; carga inválida não fica válida por insistência.**

Falha de banco, rede ou broker é repetida com espera dobrando a partir de 1 s até desistir
aos 30 s — a espera é o que impede o retry de virar tempestade contra um recurso que já está
sofrendo. Falha de desserialização e violação de contrato vão direto ao tópico de descarte,
sem queimar tentativa.

Nos dois casos a partição segue em frente e nada some em silêncio. O tópico de descarte é por
consumidor, não compartilhado, porque os dois grupos falham por motivos diferentes sobre a
mesma mensagem — o README mostra exatamente esse caso com uma carga sem `ocorridoEm`, fatal
para o painel e inofensiva para a projeção.

### 4. O que o sistema faz quando não consegue nem aceitar o trabalho

Recusa. O buffer do produtor é finito (32 MB) e a espera por espaço é limitada
(`max.block.ms: 5000`); esgotados os dois, a solicitação HTTP recebe `503` com `Retry-After`.

Responder `202 Accepted` seria mentira — o cliente ouviria "aceito" sobre um evento que não
entrou em lugar nenhum. Enfileirar em memória apenas converteria o problema em consumo de
heap, e mais tarde num `OutOfMemoryError` bem menos informativo do que um 503.

## Como executar

Siga o [README](../../README.md#como-executar-em-máquina-limpa).

Para ver o backpressure, siga [Como demonstrar backpressure](../../README.md#como-demonstrar-backpressure).

Para ver o tópico de descarte, siga [Como verificar o tópico de descarte](../../README.md#como-verificar-o-tópico-de-descarte).

## Como testar

Siga os comandos do [README](../../README.md#como-testar).

O encaminhamento ao tópico de descarte é o único comportamento desta etapa **sem** teste
automatizado — ele é verificado à mão, pelo procedimento do README. Cobri-lo exige um teste
com Kafka embarcado, previsto junto com o teste de contrato ponta a ponta.

## Quem fez o quê

```text
Gabriel Santiago - implementações aula 04
Equipe 03 — implementação, testes e documentação da Aula 04.
```
