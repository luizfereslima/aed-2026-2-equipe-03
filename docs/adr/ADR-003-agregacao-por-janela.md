# ADR-003 — Agregação por janela de tempo e escolha do relógio

## Status

Aceita · 2026-08-22 · Equipe 03

## Contexto

A Aula 03 pede um segundo consumidor, em grupo próprio, que responda a uma pergunta sobre o **fluxo** e não sobre um evento isolado. A pergunta escolhida é do organizador do show: *quantos ingressos foram emitidos para cada evento comercial a cada cinco minutos?* — o ritmo de venda que ele acompanha na abertura para decidir se libera o próximo lote.

Toda agregação por janela obriga a responder antes qual relógio marca o tempo:

- **hora de ocorrência** (*event time*): o instante do fato, lido de dentro do evento (`ocorridoEm`);
- **hora de chegada** (*processing time*): o relógio do próprio agregador no momento em que a mensagem é consumida.

O relógio de chegada está sempre à mão; o de ocorrência precisa ser lido do evento. Por isso quem não decide acaba com o de chegada por omissão.

O `venda-ingressos-consumer` da etapa 1 não pode ser afetado: ele mantém a projeção/auditoria e continua com o grupo dele.

## Decisão

Criar a aplicação independente **`venda-ingressos-painel`**, consumindo o tópico `ingressos.ingresso-emitido.v1` com o grupo `venda-ingressos-painel`, distinto do grupo do consumer da etapa 1.

1. **O relógio é o de ocorrência.** A janela de um evento é determinada pelo `ocorridoEm` dele. A decisão está isolada na classe `RelogioOcorrenciaService`, que existe para dar um lugar único e nomeado à escolha.
2. **A janela é de cinco minutos, alinhada pelo relógio.** Os limites caem sempre em `:00`, `:05`, `:10`… em UTC, calculados por truncamento do instante desde a época. Subir o processo às 10:03 não desloca nada.
3. **A agregação é contagem por `(eventoComercialId, início da janela)`.**
4. **A deduplicação é por `eventoId`.** Contar não é efeito naturalmente idempotente, e o pipeline é *at-least-once*. Como já decidido no ADR-002, a chave é a identidade do fato, nunca a de uma entidade de negócio.
5. **A tolerância de atraso é de dois minutos, e serve para publicar, não para descartar.** Passados dois minutos do fim da janela, o painel loga `JANELA FECHADA` com o número apurado. Um evento que chegar depois disso ainda entra na janela dele: o painel loga `JANELA CORRIGIDA` e o endpoint passa a mostrar o valor corrigido.
6. **O resultado é observável de fora** por `GET /painel-vendas/janelas` e pelos logs de fechamento e correção.
7. **A agregação é escrita à mão**, com as bibliotecas que o projeto já usa.

## Alternativas consideradas

- **Hora de chegada.** Recusada. É o relógio adequado para medir vazão, mas a pergunta aqui é do negócio, e um reprocessamento do tópico jogaria todo o histórico nas janelas de hoje. Além disso, atrasos do próprio pipeline — rebalanceamento, reinício, consumidor lento — apareceriam para o organizador como queda de vendas que não existiu.
- **Kafka Streams com janela e *state store*.** Recusada. Resolveria janela, estado e tolerância de atraso de graça, mas o enunciado pede explicitamente para não trocar a infraestrutura nesta etapa, e a constituição do projeto proíbe antecipar tecnologia sem necessidade concreta. Fica registrada como o caminho natural quando a agregação passar a exigir estado durável e reparticionamento.
- **Segundo `@KafkaListener` dentro do `venda-ingressos-consumer`.** Recusada. Sairia mais barato, mas misturaria numa aplicação só dois efeitos com ciclos de vida diferentes — uma projeção transacional em banco e uma apuração em memória — e tornaria menos evidente a independência entre grupos de consumidores, que é o ponto da etapa.
- **Janela deslizante ou por sessão.** Recusada por ora. A pergunta do organizador é sobre blocos comparáveis de tempo; janela fixa alinhada é o que permite comparar 20:00–20:05 com 20:05–20:10.

## Consequências aceitas

**O estado vive em memória e se perde no reinício.** A reconstrução é reprocessar o tópico do começo, o que produz exatamente o mesmo resultado — é a contrapartida direta de ter escolhido a hora de ocorrência. Persistir o estado da janela foi avaliado e adiado: enquanto a reconstrução for barata e exata, guardar o estado só cria uma segunda cópia da verdade para manter sincronizada.

**O número publicado no log pode ser corrigido depois.** Quem lê o log precisa saber que a última linha de uma janela é a que vale. A alternativa — descartar o retardatário — deixaria o log estável e a contagem errada.

**O conjunto de deduplicação cresce sem plano de expurgo**, exatamente como a tabela de eventos processados do consumer. A política de retenção sai junto com a daquele, quando ficar claro por quanto tempo uma reentrega ainda é plausível.

**Não há *watermark* com descarte.** O painel nunca declara "não aceito mais eventos desta janela". Isso significa que uma janela antiga permanece na memória para sempre, o que é aceitável no volume desta disciplina e não seria em produção.

**A agregação usa a mesma chave da partição.** Contar por `eventoComercialId`, que é também a chave de partição, evita a necessidade de repartição. Agregar por `setorId` — pergunta igualmente legítima do organizador — exigiria repartição, e fica registrada como evolução possível.

**O painel declara três dos sete campos do contrato.** É deliberado: exerce na prática a regra de compatibilidade FULL registrada em `docs/contrato.md`, e demonstra que um consumidor não precisa conhecer campo que não usa.
