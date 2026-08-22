# Registro de uso de IA

## Aula 02

### Interação 1 — escolha do evento

**O que foi pedido**

Definir o evento principal da Aula 02 para representar a emissão de ingresso.

**O que a IA sugeriu**

Comparar `IngressoEmitidoEvent` com `EmitirIngressoEvent`.

**O que foi aceito**

Foi aceito `IngressoEmitidoEvent`, porque representa um fato que já ocorreu no domínio.

**O que foi recusado**

Foi recusado `EmitirIngressoEvent`.

**Justificativa técnica da recusa**

`EmitirIngressoEvent` representa uma intenção ou comando. Eventos de domínio devem representar fatos concluídos, não solicitações de ação.

### Interação 2 — deduplicação

**O que foi pedido**

Definir como o consumer deve evitar repetir o efeito de negócio ao receber mensagens duplicadas.

**O que a IA sugeriu**

Comparar deduplicação por `eventoId` com deduplicação por `ingressoId`.

**O que foi aceito**

Foi aceita a deduplicação por `eventoId`.

**O que foi recusado**

Foi recusada a deduplicação por `ingressoId`.

**Justificativa técnica da recusa**

`eventoId` identifica o fato ocorrido. `ingressoId` identifica uma entidade de negócio. Fatos diferentes podem envolver o mesmo ingresso, portanto deduplicar por `ingressoId` poderia descartar eventos válidos.

### Interação 3 — contrato compartilhado

**O que foi pedido**

Definir como publisher e consumer devem representar o contrato `IngressoEmitidoEvent`.

**O que a IA sugeriu**

Foi considerada a criação de um módulo Maven contendo o evento compartilhado entre publisher e consumer.

**O que foi aceito**

Foi aceito manter publisher e consumer como projetos Maven independentes, cada um com sua própria classe `IngressoEmitidoEvent`.

**O que foi recusado**

Foi recusado criar um módulo Maven compartilhado para o contrato.

**Justificativa técnica da recusa**

O exercício exige publisher e consumer independentes e proíbe módulo compartilhado de contratos. A recusa influenciou a arquitetura: não existe POM pai nem dependência comum entre aplicações; o consumer declara menos campos e ignora campos desconhecidos para demonstrar evolução compatível.

## Aula 03

### Interação 1 — relógio da janela

**O que foi pedido**

Definir qual relógio a agregação por janela de tempo deve usar: hora de ocorrência do fato ou hora de chegada da mensagem no agregador.

**O que a IA sugeriu**

Comparar o relógio de ocorrência (`ocorridoEm`, lido de dentro do evento) com o relógio de chegada (a hora do próprio painel no momento do consumo), apontando que o de chegada é mais simples porque está sempre disponível e não depende de o produtor preencher a data corretamente.

**O que foi aceito**

Foi aceito o relógio de ocorrência.

**O que foi recusado**

Foi recusado o relógio de chegada.

**Justificativa técnica da recusa**

A pergunta que a agregação responde é do negócio: quantos ingressos saíram de um show em um intervalo. Com o relógio de chegada, o resultado passa a depender de quando o consumidor conseguiu processar, e atrasos do próprio pipeline — rebalanceamento, reinício, consumidor lento — apareceriam para o organizador como queda de vendas que não existiu. Além disso, reprocessar o tópico amanhã jogaria todo o histórico nas janelas de amanhã. A recusa influenciou a arquitetura: a escolha ficou isolada na classe `RelogioOcorrenciaService` e a atribuição de janela é função pura do `ocorridoEm`, o que torna o reprocessamento reprodutível.

### Interação 2 — tecnologia da agregação

**O que foi pedido**

Definir como implementar a agregação por janela de cinco minutos alinhada, com tolerância a evento atrasado.

**O que a IA sugeriu**

Adotar Kafka Streams com janela e *state store*, que resolveria janela, estado durável, tolerância de atraso e reparticionamento sem código próprio.

**O que foi aceito**

Foi aceita a agregação escrita à mão, com as bibliotecas que o projeto já usa.

**O que foi recusado**

Foi recusado adotar Kafka Streams nesta etapa.

**Justificativa técnica da recusa**

O enunciado pede explicitamente para não trocar a infraestrutura, e a constituição do projeto proíbe adicionar tecnologia sem necessidade concreta. O volume desta etapa não exige *state store*, e a agregação à mão deixa visível em código a decisão que está sendo avaliada — o alinhamento da janela e a escolha do relógio ficariam escondidos dentro do framework. A recusa influenciou o desenho: `JanelaService` calcula o alinhamento como função pura e testável, em vez de delegar a uma configuração de janela do framework.

### Interação 3 — chave de partição no contrato

**O que foi pedido**

Preencher a seção de chave de partição do `docs/contrato.md`, que deve declarar a chave usada e a ordem que ela garante.

**O que a IA sugeriu**

Documentar no contrato a chave que o código realmente usava naquele momento — `vendaId` —, já que o contrato precisa descrever o comportamento real do produtor, e ajustar depois o ADR-002 para refletir isso.

**O que foi aceito**

Foi aceito corrigir o código do publisher para publicar com `eventoComercialId`, mantendo o ADR-002 como está.

**O que foi recusado**

Foi recusado documentar `vendaId` no contrato e alterar o ADR-002 para acompanhar o código.

**Justificativa técnica da recusa**

O ADR-002 está aceito e declara `eventoComercialId` como chave de partição, com justificativa de negócio: é a menor unidade cuja ordem o domínio exige. A divergência era desvio de implementação, não decisão nova, e um ADR aceito não se reescreve para acomodar um desvio. Ordenar por `vendaId` também não garantiria nada útil, já que existe um único evento por venda. A recusa mudou o código: `PublicacaoIngressoService` passou a usar `eventoComercialId` como chave, e o teste do publisher agora verifica a chave e a grafia do tipo contra o próprio `docs/contrato.md`.

### Interação 4 — onde o novo consumidor deveria viver

**O que foi pedido**

Definir se o agregador seria uma aplicação nova ou um segundo `@KafkaListener` dentro do `venda-ingressos-consumer`, com `groupId` próprio declarado na anotação.

**O que a IA sugeriu**

Reaproveitar o `venda-ingressos-consumer` e acrescentar nele um listener com grupo próprio, evitando um terceiro projeto Maven, um terceiro Dockerfile e uma terceira porta.

**O que foi aceito**

Foi aceita a criação da aplicação independente `venda-ingressos-painel`.

**O que foi recusado**

Foi recusado hospedar o agregador dentro do consumer da etapa 1.

**Justificativa técnica da recusa**

Os dois efeitos têm ciclos de vida diferentes: a projeção é transacional em banco e a apuração é em memória, reconstruível por reprocessamento. Juntá-los num processo só acoplaria a disponibilidade de um à do outro e faria um reinício do painel arrastar o consumer. Além disso, a etapa avalia a independência entre grupos de consumidores, e ela fica demonstrável quando os dois processos sobem lado a lado e ambos recebem todas as mensagens do tópico.
