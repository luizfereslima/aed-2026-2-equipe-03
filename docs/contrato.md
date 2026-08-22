# Contrato do evento — `ingressos.ingresso.emitido.v1`

Este documento é o contrato público entre o `venda-ingressos-publisher` e quem consome o evento de emissão de ingresso. Ele descreve o que a Equipe 03 promete manter no fio, não o que uma classe Java específica contém hoje.

| | |
|---|---|
| Tipo do evento (`ce_type`) | `ingressos.ingresso.emitido.v1` |
| Tópico Kafka | `ingressos.ingresso-emitido.v1` |
| Envelope | CloudEvents 1.0, modo **binário** (metadados em headers Kafka, carga limpa em JSON) |
| Formato da carga | JSON, codificação UTF-8 |
| Produtor | `venda-ingressos-publisher` |
| Consumidores conhecidos | `venda-ingressos-consumer` (projeção/auditoria) e `venda-ingressos-painel` (agregação por janela) |
| Regra de compatibilidade | **FULL** |

A grafia do tipo e do tópico é única entre código, testes e documentação. O teste `PublicacaoIngressoServiceTest` lê o `application.yml` real e falha se este documento divergir dele.

## Envelope — CloudEvents 1.0 binário

Os metadados viajam em headers Kafka. A carga não os repete.

| Header | Tipo | Obrigatório | Significado |
|---|---|---|---|
| `ce_specversion` | string | sim | Versão da especificação CloudEvents seguida pela mensagem. Fixo em `1.0`. |
| `ce_id` | string | sim | Identidade do **fato**. É o mesmo valor do campo `eventoId` da carga — nunca o `ingressoId` nem o `vendaId`. É por ele que um consumidor decide se já processou esta mensagem. |
| `ce_source` | string | sim | Aplicação que produziu o fato, em formato de caminho: `/venda-ingressos-publisher`. Serve para o consumidor saber de onde veio o evento sem inspecionar a carga. |
| `ce_type` | string | sim | Nome versionado do contrato: `ingressos.ingresso.emitido.v1`. Um consumidor pode rotear por ele sem desserializar a carga. |
| `ce_time` | string ISO-8601 | sim | Momento em que o fato ocorreu no domínio. Repete o valor de `ocorridoEm`. |

## Carga

Todos os campos são strings JSON. Nenhum deles carrega dado pessoal: o evento trafega apenas identificadores, e quem precisar do nome do comprador consulta a origem.

| Campo | Tipo | Obrigatório | Significado |
|---|---|---|---|
| `eventoId` | string (UUID) | sim | Identifica **este fato**, e não a entidade sobre a qual ele fala. Duas mensagens com o mesmo `eventoId` são a mesma emissão entregue duas vezes; duas mensagens com `eventoId` diferentes são dois fatos distintos, ainda que falem do mesmo ingresso. É a chave de deduplicação. |
| `ocorridoEm` | string ISO-8601 com offset | sim | Instante em que o ingresso foi **emitido** no publisher, isto é, depois de a venda ter sido confirmada. Não é o instante em que o cliente pediu a compra, nem o instante em que a mensagem foi publicada, nem o instante em que ela foi consumida. É esta a hora que o painel usa para dizer em qual janela de cinco minutos a venda entrou. |
| `ingressoId` | string (UUID) | sim | Identifica o ingresso emitido — o objeto que fica com o comprador e que é apresentado na portaria. Sobrevive a este evento: outros fatos futuros sobre o mesmo ingresso (invalidação, por exemplo) repetirão este identificador. |
| `vendaId` | string (UUID) | sim | Identifica a transação comercial que deu origem ao ingresso, e é por onde se chega ao pagamento correspondente no gateway. Uma venda pode originar mais de um ingresso; hoje o publisher emite um por venda, e o contrato não promete que continuará assim. |
| `eventoComercialId` | string | sim | Identifica o **show, festival ou apresentação** para o qual o ingresso vale — nunca o fato de domínio. É por ele que a ocupação e a prestação de contas ao organizador são apuradas, e é a chave de partição do tópico. |
| `setorId` | string | sim | Identifica o setor do local dentro daquele evento comercial (pista, camarote, arquibancada). É único dentro do evento comercial, não globalmente: `setor-a` de dois shows diferentes são setores diferentes. |
| `assentoId` | string | sim | Identifica o lugar reservado dentro do setor. Único dentro do setor, não globalmente. Em setores sem lugar marcado o publisher continua enviando um identificador de vaga, para que a contagem de ocupação feche. |

### Datas

Todas as datas do contrato são **ISO-8601 com offset**, serializadas como texto e emitidas em UTC — por exemplo `2026-08-16T10:15:30Z`.

**Nunca epoch.** Um número em `ocorridoEm` quebra o consumidor sem que o esquema aparente ter mudado. O teste `IngressoEmitidoEventSerializacaoTest`, no publisher, serializa com o mesmo `JsonSerializer` configurado no `application.yml` e falha se `ocorridoEm` sair como número.

### Exemplo de carga

Dados fictícios. Nenhum nome de cliente, de empresa ou de local real aparece aqui ou no repositório.

```json
{
  "eventoId": "3f2b9c14-6a1e-4c77-9c1d-0b6f2a8e4d55",
  "ocorridoEm": "2026-08-16T10:15:30Z",
  "ingressoId": "7c9a1e02-5d43-4b18-a2f6-9e3c17b804aa",
  "vendaId": "b41d7f68-3e25-49ac-8f70-1c25de9a6033",
  "eventoComercialId": "evento-comercial-001",
  "setorId": "setor-a",
  "assentoId": "assento-a-10"
}
```

Com os headers correspondentes:

```text
ce_specversion: 1.0
ce_id:          3f2b9c14-6a1e-4c77-9c1d-0b6f2a8e4d55
ce_source:      /venda-ingressos-publisher
ce_type:        ingressos.ingresso.emitido.v1
ce_time:        2026-08-16T10:15:30Z
```

## Chave de partição e ordem garantida

A chave de partição é o **`eventoComercialId`**, conforme decidido no [ADR-002](adr/ADR-002-dominio-do-projeto.md).

O que a chave garante:

- emissões do **mesmo evento comercial** chegam a qualquer consumidor na ordem em que foram publicadas;
- **não** há ordem garantida entre eventos comerciais diferentes.

Foi essa a unidade escolhida porque é a menor cuja ordem o negócio exige: ocupação por setor, ritmo de venda e prestação de contas são todos apurados por evento comercial. Ordenar por `vendaId` não resolveria nada — só existe um evento por venda hoje — e ordem global custaria uma partição única para sempre.

Duas ressalvas honestas:

- **O tópico é criado com uma partição só** (`KafkaConfig`, no publisher). Na prática isso hoje produz ordem total. O contrato **não** promete ordem total: quem consumir deve assumir apenas a ordem por evento comercial, porque o número de partições vai crescer.
- **A chave concentra carga.** Todo o pico de abertura de vendas de um show grande cai numa partição só. O risco está registrado como consequência aceita no ADR-002, com a mitigação prevista para a Aula 04 (medir o desequilíbrio antes de mudar, e avaliar chave composta evento comercial + setor, aceitando que a ordem passaria a ser por setor).

## Regra de compatibilidade: FULL

A equipe adota **FULL**: um consumidor novo lê dado escrito por um produtor velho, e um consumidor velho lê dado escrito por um produtor novo.

**Por que FULL, e não BACKWARD ou FORWARD.** Já são três aplicações independentes — um produtor e dois consumidores — implantadas separadamente, cada uma no seu ciclo. BACKWARD obrigaria a implantar sempre os dois consumidores antes do publisher; FORWARD obrigaria o inverso. Qualquer uma das duas exige combinar uma ordem de implantação entre pessoas diferentes da equipe, numa disciplina em que cada entrega é feita em cima do prazo. FULL custa disciplina no desenho do contrato e devolve liberdade na implantação: **qualquer ordem funciona, e ninguém precisa de janela de manutenção.** À medida que aparecerem consumidores fora da equipe, essa liberdade deixa de ser conveniência e passa a ser a única opção viável — não dá para coordenar implantação com quem não se conhece.

Na prática, FULL significa estas regras operacionais:

1. campo novo entra sempre **opcional**, e o consumidor precisa funcionar sem ele;
2. campo existente não é removido nem renomeado;
3. o tipo de um campo existente não muda;
4. **o significado de um campo existente não muda** — é a regra que nenhuma ferramenta verifica;
5. consumidor ignora campo que não conhece (`@JsonIgnoreProperties(ignoreUnknown = true)` nos dois consumidores);
6. mudança que viole qualquer uma das anteriores vira `ingressos.ingresso.emitido.v2`, em tópico novo, com os dois convivendo até o último consumidor migrar.

A regra 5 não é promessa: o `venda-ingressos-painel` declara **três** dos sete campos e ignora o resto. O teste `IngressoEmitidoEventTest`, naquele módulo, desserializa a carga completa na classe reduzida e prova que um campo novo no publisher não derruba o consumidor.

## O que aconteceria se um campo mudasse

Esta seção existe porque a coluna que uma ferramenta não verifica é justamente a de significado.

**O caso perigoso: `ocorridoEm`.** Suponha que alguém decida, com boa intenção, que `ocorridoEm` deve passar a ser o instante em que o cliente **solicitou** a compra, em vez do instante em que o ingresso foi **emitido**. O tipo continua string, o formato continua ISO-8601, o esquema continua válido, nenhum teste de serialização quebra, nenhum consumidor lança exceção — e um registry aprovaria. Mas as janelas do `venda-ingressos-painel` passam a medir outra coisa: a intenção de compra, não a venda concluída. Um pagamento que demora três minutos para autorizar joga o ingresso para a janela anterior, e o organizador olha o ritmo de venda de uma abertura que não aconteceu daquele jeito. É uma mudança de contrato disfarçada de mudança de implementação. Por isso a linha de `ocorridoEm` na tabela acima diz explicitamente o que ele **não** é.

**O caso quase perigoso: `vendaId`.** Hoje há um ingresso por venda, então contar vendas e contar ingressos dá o mesmo número. No dia em que a compra de quatro ingressos virar uma venda só, quem estiver contando `vendaId` distintos para medir faturamento continua compilando e passa a errar por quatro. O contrato não promete a cardinalidade um-para-um, e esta linha existe para que ninguém a assuma.

**O caso inofensivo: um campo novo.** Acrescentar `canalDeVenda` ao payload não quebra ninguém, desde que ele entre opcional. Consumidor que não o conhece continua ignorando; consumidor que quiser usá-lo trata a ausência.
