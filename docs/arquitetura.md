# Arquitetura do sistema de venda de ingressos

## 1. O domínio

O sistema responde à pergunta: **quantos ingressos foram emitidos para cada evento comercial,
em cada janela de cinco minutos, e qual é a situação de um ingresso depois que fatos posteriores
acontecem?** O domínio é venda de ingressos para shows, festivais e apresentações.

Uma compra recebe `eventoComercialId`, `setorId` e `assentoId`. O publisher reserva o assento no
recorte atual, autoriza o pagamento por uma abstração simulada e publica o fato de emissão. O
consumer mantém a projeção de auditoria do ingresso. O painel responde à consulta operacional por
janelas de ocorrência. A arquitetura aceita consistência eventual entre publicação e projeção.

O projeto não transporta nome, CPF, e-mail, telefone ou cartão. Os fatos carregam identificadores
fictícios e mínimos. O gateway, a reserva e o estorno reais não fazem parte deste recorte.

## 2. Os eventos

`IngressoEmitidoEvent` representa emissão concluída. Possui `eventoId` próprio, `ocorridoEm`,
`ingressoId`, `vendaId`, `eventoComercialId`, `setorId` e `assentoId`. O evento usa CloudEvents
1.0 em modo binário, com metadados nos headers Kafka. `ce_id` é o `eventoId`, e `ce_time` é o
`ocorridoEm`. O contrato completo, sua compatibilidade FULL e a justificativa da chave estão em
[`docs/contrato.md`](contrato.md).

`IngressoInvalidadoEvent` é o fato de compensação. Ele possui identidade própria, data própria,
`ingressoId`, `vendaId`, `eventoComercialId` e `motivo`. A invalidação não altera o passado: o
consumer registra a reação na projeção e mantém o ingresso consultável com situação `INVALIDADO`.

Os consumidores ignoram campos desconhecidos. Um novo campo compatível deve ser opcional; remover,
renomear ou mudar o significado de campo existente exige nova versão de contrato e novo tópico.

## 3. O desenho

As aplicações são Maven independentes. O publisher não depende das classes Java dos consumidores;
cada consumidor representa localmente somente o contrato de que precisa.

```mermaid
flowchart LR
    HTTP[Cliente HTTP] --> P[venda-ingressos-publisher]
    P -->|ingressos.ingresso-emitido.v1\nchave eventoComercialId| E[(Kafka)]
    E -->|grupo venda-ingressos-consumer| C[venda-ingressos-consumer]
    E -->|grupo venda-ingressos-painel| A[venda-ingressos-painel]
    C --> DB[(H2\nprojeção/auditoria)]
    A --> API[GET /painel-vendas/janelas]
    C --> API2[GET /ingressos/{ingressoId}]
    P -->|ingressos.ingresso-invalidado.v1\nchave eventoComercialId| I[(Kafka)]
    I -->|grupo venda-ingressos-consumer| C
    C -. falha permanente .-> DC[(...dlt-consumer)]
    A -. falha permanente .-> DA[(...dlt-painel)]
    DC --> RP[POST /operacoes/dlq/reprocessamentos]
    RP -. republica payload e headers .-> E
```

O tópico de emissão possui três partições. A chave `eventoComercialId` mantém na mesma partição
os fatos do mesmo show. Assim, a ordem por evento comercial é preservada, mas não existe ordem
total entre shows distintos. O tópico de invalidação usa a mesma chave para manter a correlação.

O grupo `venda-ingressos-consumer` tem concorrência 3 e processa a projeção em transação de banco.
O grupo `venda-ingressos-painel` tem concorrência 1, porque a agregação atual é um estado em
memória protegido por seção crítica. Os grupos são independentes: cada um recebe todas as mensagens
do tópico de emissão.

## 4. As decisões

- **ADR-002 — domínio do projeto:** escolhe `eventoComercialId` como unidade de ordenação; aceita
  que um show muito grande concentre carga em uma partição quente.
- **ADR-003 — agregação por janela:** usa o relógio `ocorridoEm`; aceita que o estado das janelas
  seja perdido no reinício e reconstruído por reprocessamento.
- **ADR-004 — backpressure e falha no consumo:** deixa a fila no broker e separa retry de DLT;
  aceita operação manual e retenção adicional para os tópicos de descarte.
- **ADR-006 — resiliência e Saga:** usa quatro retries, DLT por consumidor e compensação
  coreografada; aceita consistência eventual e estado indeterminado enquanto uma compensação falha.

Os ADRs preservam a história das decisões. O arquivo `ADR-006-saga-de-compensacao.md` registra a
primeira decisão da compensação; `ADR-006-resiliencia.md` consolida as regras operacionais desta
entrega.

## 5. Quando falha

Falha transitória significa que a mesma operação pode funcionar depois: banco reiniciando,
timeout, lock/deadlock, broker temporariamente indisponível ou dependência retornando 503/429.
O container faz quatro retries com espera de 1 s, 2 s, 4 s e 8 s. Se ainda falhar, encaminha a
mensagem para a DLT.

Falha permanente significa que a mensagem não ficará válida por esperar: JSON inválido, data sem
formato, campo necessário ausente ou violação de contrato. `ErrorHandlingDeserializer` permite
que a falha chegue ao tratador; `IllegalArgumentException` de validação é marcado como não
retentável. A partição avança depois do encaminhamento, sem descarte silencioso.

O `DeadLetterPublishingRecoverer` deriva os destinos:

- `ingressos.ingresso-emitido.v1.dlt-consumer` e
  `ingressos.ingresso-invalidado.v1.dlt-consumer` para a projeção;
- `ingressos.ingresso-emitido.v1.dlt-painel` para a agregação.

O payload original e os headers CloudEvents, inclusive `ce_id`, são mantidos. Headers adicionais
registram exceção, mensagem, tópico, partição e offset de origem. Uma DLT é inspecionada por
operação; não há consumidor automático que devolva a mensagem em laço.

Depois que a causa é corrigida, a operação chama:

```bash
curl -X POST http://localhost:8081/operacoes/dlq/reprocessamentos \
  -H 'Content-Type: application/json' \
  -d '{"topico":"ingressos.ingresso-emitido.v1.dlt-consumer","particao":0,"offset":12}'
```

O endpoint lê o offset específico com bytes crus, valida o cabeçalho do tópico original e
republica payload, chave, partição e headers. Não seleciona lote nem corrige dado silenciosamente.
Se a mesma mensagem for reprocessada duas vezes, o consumer não duplica o efeito porque a chave de
deduplicação é `eventoId`.

A Saga segue o caminho: emissão confirmada → `IngressoEmitidoEvent` → falha/decisão de invalidação
→ `IngressoInvalidadoEvent` → projeção `INVALIDADO`. Se a compensação falhar, ela tem os mesmos
quatro retries e a mesma DLT; a projeção pode ficar `EMITIDO` até a correção e o reprocessamento.
Esse estado é indeterminado e explícito, não uma licença para UPDATE manual ou DELETE.

## 6. Quando cresce

O primeiro gatilho de escala é o **lag por grupo e partição**, não CPU. CPU baixa pode coexistir
com uma fila grande quando o listener espera banco ou rede. O número de instâncias úteis é limitado
às três partições do tópico: uma partição só é atribuída a um consumidor do grupo. Subir quatro
instâncias para três partições adicionaria custo sem vazão.

O consumer de projeção pode usar até três instâncias efetivas. O painel tem um gargalo adicional:
seu estado global em memória é uma seção crítica única. Para escalá-lo de verdade seria necessário
particionar o estado por `eventoComercialId` ou adotar uma store compartilhada, decisão fora deste
escopo.

`eventoComercialId` mantém ordem do show, mas cria partição quente na abertura de um evento com
muito volume. Mais réplicas não dividem essa chave entre partições. A alternativa futura seria
`eventoComercialId + setorId`, aceitando trocar a ordem por evento pela ordem por setor. Essa troca
só deve ocorrer depois de medir o desequilíbrio e registrar novo ADR.

Escalar também tem custo de rebalanceamento. Subir, remover ou reiniciar instâncias redistribui
partições e pausa o grupo; um autoscaling agressivo pode gastar mais tempo rebalanceando que
processando. O limite `max.poll.interval.ms` deve continuar maior que lote × custo máximo do
registro, especialmente no painel quando o atraso de demonstração estiver ligado.

## 7. O que se enxerga

As quatro perguntas operacionais abaixo são respondidas com sinais do evento e do broker:

| Pergunta | Sinal | Diagnóstico |
|---|---|---|
| O consumidor acompanha quem publica? | Lag por grupo e partição | Lag crescente em uma partição aponta consumidor parado ou chave quente. |
| Há quanto tempo o cliente espera? | Idade do evento mais antigo não processado | Distingue 40 eventos recentes de 40 eventos parados há horas. |
| Quanto do fluxo está parando? | Taxa de entrada na DLT | Salto indica contrato inválido, deploy incompatível ou regra nova rejeitando mensagens. |
| Quanto demora do fato ao efeito? | `agora - ocorridoEm` no processamento | Mostra latência de ponta a ponta e não apenas CPU do processo. |

Os logs de publicação e consumo incluem `eventoId`, `eventoComercialId`, tópico, partição e
offset. A consulta do ingresso mostra a situação e o motivo de invalidação. O painel registra
`JANELA FECHADA` e `JANELA CORRIGIDA` para que um evento atrasado seja distinguido de uma perda.
Uma investigação começa pelo grupo/partição, localiza o offset e segue pelo `ce_id` no evento e
na DLT.

Ainda não há tracing distribuído nem backend de métricas. Os sinais acima podem ser extraídos dos
comandos Kafka e dos logs desta entrega; adicionar métricas exportadas e `traceparent` é evolução
quando houver requisito operacional real.

## 8. O que ficou de fora

- **Outbox e publicação transacional:** o publisher ainda aceita dual-write entre venda simulada e
  Kafka. Outbox exigiria persistência da venda e uma política de relay, inexistentes no recorte.
- **Gateway, estorno e reserva reais:** `PagamentoService` e assento são abstrações simuladas;
  não há regra institucional inventada para prazo, antifraude ou dinheiro.
- **Persistência do painel:** janelas em memória são reconstruídas relendo o tópico; uma store
  durável só vale quando houver requisito de consulta após reinício sem replay.
- **Escala além da chave:** não há reparticionamento composto nem Kafka Streams; ambos mudariam as
  garantias e o custo operacional.
- **Segurança operacional:** o endpoint de reprocessamento deve ficar em rede administrativa e
  receber autenticação/autorização antes de qualquer exposição externa.
- **Retenção e alerta automático:** DLT, deduplicação e logs precisam de política de retenção,
  métricas e dono de operação. O projeto registra essa dívida em vez de fingir que ela não existe.

Estas exclusões são deliberadas. Implementá-las sem requisito mudaria a arquitetura, aumentaria o
custo de operação e esconderia as decisões que este sistema precisa tornar verificáveis.
