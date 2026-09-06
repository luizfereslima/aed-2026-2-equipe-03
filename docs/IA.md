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

## Aula 04

> `TODO_EQUIPE:` as decisões abaixo foram propostas pela IA a partir do estado do repositório e ainda **não** foram validadas pela equipe. O ADR-004 está como *Proposta* pelo mesmo motivo. Revisar, aceitar ou recusar, e então ajustar este registro.

### Interação 1 — programação reativa

**O que foi pedido**

Definir como introduzir backpressure no sistema, dado que a aula trata de Programação Reativa e Backpressure.

**O que a IA sugeriu**

Comparar a adoção de Reactor (`reactor-kafka` nos consumidores, WebFlux no publisher) com tornar explícito o controle de fluxo que o consumidor Kafka já exerce.

**O que foi aceito**

Foi aceito tornar explícito o mecanismo existente: `max.poll.records` escolhido em função do custo por registro, `concurrency` decidido por consumidor, e `ack-mode: manual` com confirmação após o retorno do service.

**O que foi recusado**

Foi recusada a adoção de Reactor nesta etapa.

**Justificativa técnica da recusa**

O consumidor Kafka já é *pull-based*: o contêiner não busca o próximo lote antes de terminar o anterior, e a fila fica no broker em vez de na memória do processo. Backpressure é o comportamento nativo do laço de poll, não algo que faltasse. Acrescentar `Flux` por cima seria uma segunda camada de controle de fluxo sobre uma que já funciona, contra a proibição de overengineering da constituição. A recusa está condicionada ao enunciado: se ele exigir programação reativa nominalmente, o ADR-004 precisa ser revisto — há um `TODO_EQUIPE` registrado ali para isso.

### Interação 2 — política de retry

**O que foi pedido**

Definir o que os consumidores fazem diante de uma mensagem que falha no processamento.

**O que a IA sugeriu**

Comparar uma política única de retry para toda falha com a classificação da falha em transitória e não repetível.

**O que foi aceito**

Foi aceita a classificação: falha transitória é repetida com espera dobrando de 1 s até 30 s; falha de desserialização e violação de contrato vão direto ao tópico de descarte.

**O que foi recusado**

Foi recusada a política única de retry, em qualquer das duas formas — tentar sempre, ou desistir sempre.

**Justificativa técnica da recusa**

As duas falhas têm naturezas opostas. Insistir numa carga malformada dá o mesmo resultado em todas as tentativas e trava a partição enquanto isso; desistir de uma falha de banco descarta uma mensagem íntegra por causa de uma indisponibilidade que ia passar. A espera crescente existe pelo mesmo motivo: retry imediato contra um recurso já sobrecarregado é tempestade, não recuperação.

### Interação 3 — tópico de descarte

**O que foi pedido**

Definir se o `venda-ingressos-consumer` e o `venda-ingressos-painel` compartilhariam um tópico de descarte.

**O que a IA sugeriu**

Comparar um tópico único, mais simples de operar, com um tópico por consumidor.

**O que foi aceito**

Foi aceito um tópico de descarte por consumidor.

**O que foi recusado**

Foi recusado o tópico compartilhado.

**Justificativa técnica da recusa**

Os dois grupos falham por motivos diferentes sobre a mesma mensagem. Uma carga sem `ocorridoEm` é fatal para a janela do painel e irrelevante para a projeção, que nem lê aquele campo para decidir. Um descarte comum misturaria as duas histórias e tiraria de cada consumidor o direito de ter a própria política de falha — que é justamente o que a independência entre grupos, estabelecida na Aula 03, deveria preservar.

### Interação 4 — ordem da deduplicação no painel

**O que foi pedido**

Revisar o `PainelVendasService` diante da introdução de retry e de tópico de descarte.

**O que a IA sugeriu**

A IA apontou que o `registrar` inseria o `eventoId` no conjunto de eventos apurados antes de calcular a janela, que podia lançar em seguida — de modo que um evento que falhasse ficaria marcado como apurado sem nunca ter sido contado, e a reentrega cairia no desvio de duplicata.

**O que foi aceito**

Foi aceita a inversão da ordem para validar, marcar e então contar, com teste de regressão cobrindo a reentrega após falha.

**O que foi recusado**

Foi recusada a alternativa de remover o `eventoId` do conjunto dentro de um bloco de tratamento de exceção.

**Justificativa técnica da recusa**

Desfazer a marca no caminho de exceção depende de o tratamento cobrir toda exceção possível, inclusive as que ainda não existem no método. A ordem correta não depende de ninguém lembrar disso: enquanto a marca vier depois de tudo que pode falhar, não há o que desfazer. É o mesmo princípio que o `venda-ingressos-consumer` obtém do `@Transactional`.

### Interação 5 — serializador do tópico de descarte

**O que foi pedido**

Definir como a carga que falhou na desserialização é escrita no tópico de descarte, já que ela não é um objeto do domínio e sim bytes crus.

**O que a IA sugeriu**

Comparar um serializador que despacha por tipo — bytes crus preservados como bytes, objeto serializado em JSON — com o uso do `JsonSerializer` para os dois casos.

**O que foi aceito**

Foi aceito o `JsonSerializer` único, com a consequência de que a carga malformada chega ao descarte em base64.

**O que foi recusado**

Foi recusado o serializador que despacha por tipo.

**Justificativa técnica da recusa**

Perde-se legibilidade imediata da carga malformada e ganha-se um caminho só, sem uma segunda peça de configuração para manter. Os cabeçalhos de diagnóstico que o Spring acrescenta continuam legíveis, e são eles que dizem qual foi a exceção, o tópico, a partição e o offset de origem — que é o que se procura primeiro ao investigar um descarte. A decisão está registrada como consequência aceita no ADR-004, e não como detalhe de implementação.
