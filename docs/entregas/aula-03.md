# Entrega — Aula 03

## O que foi feito

**Parte A** — o contrato do `IngressoEmitidoEvent`, que até aqui existia espalhado entre as classes dos dois lados, virou documento explícito em [docs/contrato.md](../contrato.md): campos com tipo, obrigatoriedade e significado, formato de data, chave de partição, regra de compatibilidade e exemplo de carga.

**Parte B** — nasceu o `venda-ingressos-painel`, um segundo consumidor do mesmo tópico, em grupo próprio, que agrega o fluxo por janela de cinco minutos alinhada pelo relógio.

Durante a etapa apareceu um desvio: o ADR-002 declara `eventoComercialId` como chave de partição, mas o publisher estava publicando com `vendaId`. Como o contrato precisa declarar a chave e a ordem que ela garante, o código foi corrigido para o que o ADR aceito diz. O `venda-ingressos-consumer` da etapa 1 não foi tocado e continua funcionando.

## Onde está cada artefato

- [Contrato do evento](../contrato.md) — Parte A
- [Painel de vendas](../../venda-ingressos-painel) — Parte B
- [ADR-003 — Agregação por janela de tempo](../adr/ADR-003-agregacao-por-janela.md)
- [Registro de uso de IA](../IA.md)
- [README](../../README.md)

## As quatro perguntas

### 1. Qual pergunta de negócio a agregação responde

**"Quantos ingressos foram emitidos para cada show a cada cinco minutos?"**

É a pergunta que o organizador faz na abertura de vendas, olhando o painel enquanto a fila anda: o ritmo caiu porque o lote acabou ou porque o interesse acabou? Vale abrir o próximo lote agora ou esperar? Compensa liberar mais um setor?

Não é "quantos eventos por minuto". A unidade não é mensagem, é **ingresso emitido**; o agrupamento não é o tópico, é o **evento comercial**; e o número serve para uma decisão comercial, não para dimensionar consumidor.

### 2. Qual relógio foi escolhido, e por quê

**Hora de ocorrência** — o campo `ocorridoEm` de dentro do evento, que é o instante em que o ingresso foi emitido no publisher.

Porque a pergunta é sobre o domínio. "Saíram 340 ingressos deste show entre 20:00 e 20:05" é uma frase sobre o show. Com o relógio de chegada, o mesmo número passaria a depender de quando o painel conseguiu consumir a mensagem: uma pausa de rebalanceamento, um reinício ou um consumidor lento empurrariam vendas das 20:00 para a janela das 20:10, e o organizador leria um vale onde não houve vale.

A escolha está materializada em código na classe `RelogioOcorrenciaService`, que existe para ter um lugar onde a decisão está escrita, e registrada no [ADR-003](../adr/ADR-003-agregacao-por-janela.md).

### 3. O que acontece com um evento que chega atrasado

Ele cai na janela a que pertence pelo `ocorridoEm` dele — **sempre**, inclusive depois de aquela janela já ter sido dada por fechada.

O painel espera dois minutos além do fim da janela antes de publicar o resultado no log (`JANELA FECHADA`). Essa tolerância adia a divulgação; ela **não** fecha a porta. Se um retardatário chegar depois, o painel soma, loga `JANELA CORRIGIDA` com o valor novo e o endpoint `/painel-vendas/janelas` passa a mostrar o número corrigido. Nada é descartado.

A consequência aceita é que o número publicado no log pode ser corrigido depois — quem lê o log precisa saber que a última linha de uma janela é a que vale. Descartar o retardatário deixaria o log estável e a contagem errada; preferimos o inverso.

### 4. Se o fluxo fosse reprocessado do começo amanhã, o resultado seria o mesmo?

**Sim**, e é por isso que o relógio de ocorrência foi escolhido.

A janela de cada evento é função pura do `ocorridoEm` dele, e a contagem é deduplicada por `eventoId` — mensagem repetida não conta duas vezes. Reprocessar o tópico inteiro amanhã, na ordem que for, produz exatamente as mesmas janelas com os mesmos números. O teste `deveProduzirOMesmoResultadoAoReprocessarEmOutraOrdem` reprocessa o fluxo invertido e duplicado e compara o resultado.

O que **não** sobrevive é o estado: o painel mantém as janelas em memória, então reiniciar o processo zera o painel. A reconstrução é reprocessar o tópico desde o começo — o que dá o mesmo resultado, justamente pelo motivo acima. Persistir o estado da janela é o desafio opcional desta aula, e a equipe escolheu não fazê-lo: enquanto a reconstrução é barata e exata, guardar o estado só adicionaria uma cópia da verdade para manter sincronizada.

## Como executar

Siga o [README](../../README.md#como-subir-o-painel-de-vendas).

## Como testar

Siga os comandos do [README](../../README.md#como-testar).

## Quem fez o quê

```text
Gabriel Grapeggia Ceola — Contrato do evento, painel de vendas com agregação por janela, correção da chave de partição e documentação da etapa.
Luiz Felipe Dias Cardoso Feres Lima — Revisao e auxilio nas ideias do projeto.
TODO_EQUIPE: Nome — responsabilidade
Daniel da Silveira Moreira — Revisão do projeto e avaliação do estado atual da implementação.
```
