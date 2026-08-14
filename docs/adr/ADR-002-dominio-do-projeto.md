# ADR-002 — Domínio do projeto

## Status

Aceita · 2026-08-14 · Equipe 03

## Contexto

A disciplina Arquitetura Reativa e Event-Driven exige um domínio incremental que permita discutir comunicação orientada a eventos, idempotência, consistência eventual, compensação, CQRS e Event Sourcing em aulas futuras.

O domínio escolhido é venda de ingressos para eventos. O processo envolve solicitação de compra, reserva temporária de assento, autorização de pagamento em gateway externo, confirmação de venda e emissão de ingresso.

O domínio foi trazido por **Luiz Felipe Dias Cardoso Feres Lima** e **Gabriel Santiago Silva**. A experiência real vem do lado do organizador: Gabriel já organizou eventos e vendeu ingressos por plataformas de bilheteria, e conhece de uso o processo que vai da compra à validação na portaria — a reserva que expira, o pagamento que confirma depois, o cancelamento e o estorno, a lista de check-in.

Registramos o limite dessa vivência: nenhum integrante trabalhou no **desenvolvimento** de plataformas de bilheteria. Conhecemos o processo de negócio, não a implementação de um sistema real, e as regras declaradas aqui são decisão desta equipe.

O termo evento possui ambiguidade no domínio: evento comercial representa show, festival ou apresentação; evento de domínio representa fato ocorrido no sistema. Por isso, o código e a documentação usam `EventoComercial` para a apresentação e `IngressoEmitidoEvent` para o fato de domínio.

## Decisão

O projeto adotará o domínio de venda de ingressos para eventos.

Fluxo principal:

1. Cliente solicita a compra de ingresso para determinado evento comercial, setor e assento.
2. O publisher avalia se a solicitação contém os identificadores necessários para reserva.
3. O assento é tratado como reservado no recorte inicial da aula.
4. O pagamento é solicitado por meio de uma abstração de gateway externo chamada `PagamentoService`.
5. Após autorização simulada do pagamento, a venda é confirmada.
6. O ingresso é emitido.
7. O publisher publica `IngressoEmitidoEvent` no Kafka.
8. O consumer processa o evento de forma idempotente e registra projeção/auditoria de ingresso emitido.

Critérios do domínio:

- **ponto de decisão com regra de negócio**: a confirmação da venda. Três regras, com os valores fixados por esta equipe:
  - a reserva do assento **expira em 10 minutos** contados da solicitação. Autorização que chega depois disso não confirma a venda: o assento já voltou ao lote e o pagamento segue para estorno;
  - **limite de 6 ingressos por comprador por evento comercial**, somando emitidos e reservados;
  - **meia-entrada limitada a 40% dos ingressos do evento comercial**, verificada por declaração no ato da compra;
- **sistema externo**: gateway de pagamento;
- **caminho de exceção com compensação**: liberação do assento e estorno do pagamento quando uma operação posterior falhar, ou quando o evento comercial for cancelado pelo organizador — caso em que o estorno é em massa e o ingresso já emitido precisa ser invalidado;
- **algo que valha reprocessar**: projeção/auditoria de ingressos emitidos, ocupação por setor e prestação de contas ao organizador (vendido, estornado, valor de repasse).

O evento principal da Aula 02 é `IngressoEmitidoEvent`, pois representa um fato concluído. O contrato publicado contém `eventoId`, `ocorridoEm`, `ingressoId`, `vendaId`, `eventoComercialId`, `setorId` e `assentoId`. Nenhum dado pessoal trafega no evento: o consumer trabalha por identificadores.

A chave de partição é o `eventoComercialId` — é a menor unidade cuja ordem o negócio exige, já que a ocupação e a prestação de contas são apuradas por evento comercial. A justificativa aprofundada fica para a Aula 04.

O `ce_type` adotado é `ingressos.ingresso.emitido.v1`, em grafia única no código, nos testes e na documentação.

## Alternativas consideradas

- **Emissão fiscal / faturamento com órgão regulador**: o sistema externo seria o melhor possível, com indisponibilidade real e resposta assíncrona. Recusado porque o recorte mínimo exigiria regra fiscal que nenhum integrante domina o bastante para defender, e inventá-la contaminaria todas as etapas seguintes.
- **Cobrança recorrente e inadimplência**: atende os quatro critérios com folga e tem efeito irreversível útil para Saga. Recusado porque nenhum integrante tem vivência do lado do credor, e a régua de cobrança seria arbitrada por nós.

## Consequências aceitas

**O que fica de fora do escopo nesta etapa**: antifraude, validação documental de meia-entrada, revenda entre compradores, e o gateway de pagamento real — usamos uma abstração simulada.

A arquitetura aceita consistência eventual entre emissão do ingresso no publisher e projeção/auditoria no consumer.

Mensagens Kafka podem ser entregues mais de uma vez. Por isso, o consumer deve ser idempotente e deduplicar por `eventoId`, não por `ingressoId`, `vendaId` ou outro identificador de entidade. O efeito de negócio do consumer e o registro do evento processado devem ocorrer no mesmo commit de banco, e o ACK do Kafka somente depois do retorno do service transacional.

**A reserva do assento não será resolvida por eventos.** Concorrência por assento único pede consistência forte, e este pipeline é at-least-once com entrega assíncrona. Assumimos que a reserva acontece de forma síncrona e transacional no publisher, e que só o que vem depois dela trafega por evento. É escolha, não esquecimento.

**Aula 04 — a chave de partição escolhida vai criar partição quente.** Particionar por `eventoComercialId` preserva a ordem que o negócio exige, mas concentra num único parceiro todo o pico de abertura de vendas de um show grande — exatamente quando o volume aparece. Mitigação prevista: medir o desequilíbrio antes de mudar e avaliar chave composta por evento comercial e setor, aceitando que a ordem passaria a ser garantida por setor.

**Aula 05 — a compensação não é rollback.** Estornar um pagamento depois da emissão não desfaz o mundo: o ingresso já está com o comprador. A Saga terá de trabalhar com compensação semântica — invalidar o ingresso, liberar o assento e comunicar — e o estado intermediário "emitido mas invalidado" precisa existir no modelo e aparecer no fluxo de leitura da portaria. Será necessária correlação entre venda, pagamento, reserva e ingresso.

**Aula 05 — a projeção é reconstruível, e a tabela de deduplicação cresce sem plano de expurgo.** A projeção de ingressos emitidos poderá ser reconstruída a partir dos eventos, mas a Aula 02 não implementa Event Sourcing nem CQRS. A política de retenção da chave de deduplicação será definida junto com eles, quando ficar claro por quanto tempo uma reentrega ainda é plausível.

**Publicação dentro do fluxo de venda.** Nesta etapa o publisher confirma a venda e publica o evento no mesmo fluxo, o que é dual-write: a venda pode ser confirmada e a publicação falhar, ou o inverso. Aceitamos o risco agora, com a falha de publicação tratada e registrada, e adiamos o padrão outbox para quando houver requisito que o justifique — a constituição do projeto proíbe antecipar infraestrutura sem necessidade concreta.

**Conhecemos o processo pelo lado de fora.** Detalhes de integração com gateway, política de invalidação de ingresso e prazos de repasse são modelados a partir do que observamos como organizadores e clientes, e podem divergir de uma plataforma real. O que se avalia aqui é a coerência das regras declaradas. Toda regra que não vier dessa vivência fica marcada com `TODO_DOMINIO:` até a equipe decidi-la por escrito.

Mitigações gerais previstas: manter eventos com identidade própria, versionar contratos, preservar idempotência e registrar novas decisões arquiteturais por ADR.
