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
