# Constituição do Projeto

Este repositório pertence à disciplina Arquitetura Reativa e Event-Driven (AED) da PUC Minas.

O projeto é acadêmico, incremental e tem como domínio permanente a venda de ingressos para eventos. Cada nova aula deve evoluir o estado existente do projeto, não reiniciar a arquitetura como se fosse um projeto novo.

## Hierarquia Das Instruções

Ao trabalhar neste repositório, siga esta ordem de precedência:

1. Enunciado oficial da aula atual.
2. Decisões arquiteturais registradas em ADRs aceitos.
3. Esta constituição (`AGENTS.md`).
4. Documentação complementar do repositório.
5. Convenções inferidas do código existente.

Quando houver conflito:

- não escolher silenciosamente uma interpretação;
- identificar o conflito;
- preservar requisitos acadêmicos explícitos;
- registrar decisão arquitetural quando necessário;
- não alterar ADR aceito silenciosamente.

Este arquivo não deve duplicar integralmente os enunciados das aulas. Requisitos específicos de cada aula pertencem à documentação daquela entrega, não à constituição.

## Contexto Permanente

- O projeto pertence à disciplina Arquitetura Reativa e Event-Driven.
- O projeto será evoluído incrementalmente durante várias aulas.
- O domínio é venda de ingressos para eventos.
- Novas aulas adicionarão requisitos, técnicas e conceitos.
- Implementações futuras devem preservar compatibilidade com entregas anteriores sempre que possível.
- Decisões arquiteturais relevantes devem ser documentadas por ADR.

## Princípios De Evolução

Regras obrigatórias:

- compreender o estado atual antes de modificar;
- realizar alterações incrementais;
- preservar comportamentos existentes não afetados pelo novo requisito;
- evitar refatorações não relacionadas à tarefa;
- não reescrever componentes funcionando apenas por preferência;
- manter retrocompatibilidade quando tecnicamente razoável;
- atualizar testes quando comportamento mudar;
- atualizar documentação quando arquitetura ou execução mudar;
- registrar decisões arquiteturais relevantes.

Antes de implementar uma nova etapa, o agente deve:

1. ler `AGENTS.md`;
2. ler `README.md`;
3. ler ADRs existentes;
4. ler a documentação das entregas anteriores relevante;
5. ler integralmente o requisito/enunciado da tarefa atual;
6. inspecionar o código relacionado;
7. identificar impactos;
8. somente então propor ou executar alterações.

## Stack Base

Stack base atual:

- Java 21;
- Spring Boot;
- Maven;
- Apache Kafka;
- Docker Compose.

Não fixar versões específicas neste arquivo quando elas já forem controladas pelos arquivos Maven ou Docker.

Não adicionar framework, biblioteca, banco, broker ou infraestrutura sem necessidade concreta. Antes de adicionar uma dependência, verificar se o problema pode ser resolvido adequadamente com recursos já existentes.

## Arquitetura

Princípios arquiteturais:

- baixo acoplamento;
- alta coesão;
- responsabilidades explícitas;
- domínio protegido de detalhes de infraestrutura;
- comunicação orientada a eventos quando fizer sentido para o processo;
- contratos de eventos tratados como contratos públicos entre aplicações;
- evolução independente entre produtor e consumidor;
- evitar dependências compartilhadas que criem acoplamento desnecessário.

Publisher e consumer devem permanecer aplicações independentes quando essa separação fizer parte da arquitetura vigente.

Não criar biblioteca compartilhada de eventos apenas para evitar duplicação de classes. Duplicação controlada de representação de contrato entre produtor e consumidor é aceitável quando preserva independência.

## Organização Do Código

Enquanto não houver decisão posterior registrada em ADR ou requisito acadêmico explícito em contrário, utilizar os pacotes principais definidos pela disciplina:

- raiz;
- `controller`;
- `domain`;
- `service`.

Não criar arbitrariamente pacotes genéricos como:

- `utils`;
- `helpers`;
- `managers`;
- `impl`;
- `dto`;
- `entities`;
- `events`;
- `infrastructure`;
- `kafka`.

Se uma aula futura exigir mudança estrutural, o novo requisito prevalece e a decisão deve ser documentada quando arquiteturalmente relevante.

## Nomenclatura

Utilizar raiz em português com sufixo técnico em inglês.

Exemplos:

- `IngressoEmitidoEvent`;
- `IngressoController`;
- `IngressoService`;
- `IngressoRepository`;
- `IngressoListener`;
- `AssentoVO`;
- `VendaApplication`;
- `KafkaConfig`.

Evitar mistura desnecessária de inglês e português no conceito de negócio.

Não utilizar nomes genéricos como:

- `Util`;
- `Helper`;
- `Manager`;
- `Impl`.

Não utilizar nomes orientados à tecnologia quando o papel de negócio ou aplicação puder ser expresso claramente. Por exemplo, evitar uma classe chamada `KafkaProducer` se existir um nome que represente melhor sua responsabilidade.

## Eventos De Domínio

Todo `Event` deve representar um fato que já ocorreu.

Preferir:

- `IngressoEmitidoEvent`;
- `PagamentoAutorizadoEvent`;
- `AssentoReservadoEvent`.

Evitar:

- `EmitirIngressoEvent`;
- `AutorizarPagamentoEvent`;
- `ReservarAssentoEvent`.

Comandos representam intenção. Eventos representam fatos concluídos.

Não criar eventos CRUD genéricos como `IngressoAtualizadoEvent`, `VendaAlteradaEvent` ou `RegistroCriadoEvent` quando existir um fato de negócio mais expressivo.

## Identidade Dos Eventos

Todo evento deve possuir identidade própria.

`eventoId` representa a identidade do fato.

Não confundir `eventoId` com:

- `ingressoId`;
- `vendaId`;
- `assentoId`;
- `pagamentoId`;
- `eventoComercialId`.

Eventos diferentes podem se referir à mesma entidade. Quando houver deduplicação, a identidade do evento deve ser considerada, não automaticamente a identidade da entidade de negócio.

## Imutabilidade E Contratos

Eventos devem ser explicitamente imutáveis conforme os requisitos vigentes da disciplina.

Quando aplicável:

- campos privados e finais;
- ausência de setters;
- cópia defensiva de coleções;
- datas serializadas em ISO-8601;
- evitar informações desnecessárias no payload.

Não alterar contrato de evento existente sem avaliar consumidores.

Antes de adicionar um campo, perguntar: "O consumidor realmente precisa receber essa informação?"

Preferir payload mínimo suficiente.

## CloudEvents

Enquanto permanecer requisito vigente, utilizar CloudEvents 1.0 para metadados das mensagens.

Preservar consistência entre:

- `specversion`;
- `id`;
- `source`;
- `type`;
- `time`.

O `type` deve possuir convenção estável e versionada.

Exemplo conceitual:

```text
ingressos.ingresso.emitido.v1
```

Nunca criar grafias diferentes do mesmo `type` em código, testes e documentação.

`ce_id` deve representar a identidade do evento.

`ce_time` representa quando o fato ocorreu no domínio.

## Kafka

Toda decisão relacionada a Kafka deve considerar explicitamente:

- chave de partição;
- ordenação necessária;
- semântica de entrega;
- duplicidade;
- retry;
- falhas;
- commit/ack;
- evolução de contratos.

Não escolher partition key aleatoriamente. A chave deve representar a unidade de negócio cuja ordenação precisa ser preservada.

Nunca ignorar silenciosamente falhas de publicação.

Não introduzir complexidade de Kafka sem necessidade demonstrável.

## Idempotência

Consumidores que executem efeitos não naturalmente idempotentes devem ser projetados considerando mensagens duplicadas.

Quando for utilizada deduplicação:

- utilizar `eventoId`;
- não utilizar automaticamente ID da entidade;
- garantir atomicidade entre efeito de negócio e memória da deduplicação, quando necessário;
- confirmar processamento somente após sucesso da operação correspondente.

Testes devem demonstrar o comportamento esperado diante de duplicidade.

## Transações

Manter limites transacionais explícitos.

Enquanto permanecer a convenção vigente da disciplina, `@Transactional` deve existir somente em `Service`.

Controllers e listeners devem atuar como adaptadores e delegar regras e transações aos services.

Evitar transações distribuídas sem necessidade explícita.

## Evolução De Contratos

Consumidores devem ser tolerantes à evolução compatível dos eventos quando aplicável.

Um consumidor não deve precisar conhecer campos que não utiliza.

Antes de realizar breaking change:

1. identificar produtores e consumidores afetados;
2. verificar possibilidade de evolução compatível;
3. considerar versionamento;
4. registrar decisão relevante;
5. criar testes de compatibilidade quando apropriado.

Não alterar contrato apenas para "deixar mais bonito".

## Dados E Privacidade

Aplicar minimização de dados.

Eventos não devem carregar dados pessoais sem necessidade real.

Evitar especialmente:

- CPF;
- número completo de cartão;
- dados bancários;
- telefone;
- e-mail;
- informações pessoais desnecessárias.

Exemplos e testes devem utilizar dados fictícios.

Identificadores devem ser preferidos quando o consumidor puder trabalhar sem receber dados pessoais.

## Testes

Código novo ou comportamento alterado deve possuir testes proporcionais ao risco.

Priorizar testes que validem:

- regras de negócio;
- contratos;
- idempotência;
- serialização;
- integração publisher/Kafka/consumer quando aplicável;
- regressões importantes.

Antes de concluir uma tarefa:

- executar testes dos módulos afetados;
- não declarar sucesso se testes relevantes estiverem falhando;
- informar claramente testes que não puderam ser executados.

Não remover ou enfraquecer teste apenas para fazer a suíte passar.

## Documentação

Preservar os caminhos acadêmicos definidos pelo projeto, incluindo:

- `README.md`;
- `docs/adr/`;
- `docs/entregas/`;
- `docs/IA.md`.

Não mover arquivos exigidos pelo professor.

`README.md` deve permanecer suficiente para que outra pessoa consiga entender e executar o projeto.

Alterações que mudem instalação, execução, configuração, arquitetura, contrato ou infraestrutura devem atualizar a documentação correspondente.

## ADR

Utilizar ADR para decisões arquiteturais relevantes e duradouras.

Exemplos:

- mudança significativa de arquitetura;
- escolha de estratégia de consistência;
- adoção de nova infraestrutura;
- mudança de estratégia de contratos;
- decisão entre alternativas com trade-offs relevantes.

Não criar ADR para toda pequena alteração de código.

Nunca sobrescrever silenciosamente a história de um ADR aceito.

Quando uma decisão for substituída, preservar histórico e registrar a nova decisão adequadamente.

## Uso De IA

A IA deve atuar como apoio técnico, não como fonte de regras inexistentes.

Nunca inventar:

- regra comercial;
- política de cancelamento;
- prazo;
- taxa;
- limite;
- integração real;
- comportamento institucional;
- informação sobre integrantes da equipe.

Quando faltar informação, utilizar explicitamente:

- `TODO_EQUIPE:`;
- `TODO_DOMINIO:`.

Também é aceitável solicitar esclarecimento quando a ausência impedir uma decisão segura.

A IA deve distinguir claramente:

- requisito explícito;
- decisão arquitetural existente;
- sugestão;
- hipótese;
- informação desconhecida.

Quando uma interação relevante com IA influenciar uma decisão da entrega, registrar em `docs/IA.md` conforme exigência acadêmica vigente.

## Proibição De Overengineering

Este é um projeto acadêmico incremental.

Não adicionar padrões ou tecnologias apenas para demonstrar sofisticação.

Não introduzir automaticamente:

- Kubernetes;
- service mesh;
- API Gateway;
- Redis;
- Schema Registry;
- observabilidade distribuída completa;
- múltiplos bancos;
- frameworks adicionais;
- abstrações genéricas;
- arquitetura hexagonal completa;
- DDD tático completo;
- Spec Kit.

Esses itens só podem ser adicionados mediante requisito concreto ou decisão arquitetural justificada.

Preparar para evolução não significa implementar antecipadamente.

YAGNI deve ser considerado.

## Escopo Das Alterações

Ao receber uma tarefa:

- modificar somente o necessário;
- não aproveitar para refatorar partes não relacionadas;
- não renomear classes sem necessidade;
- não alterar contratos sem motivo;
- não atualizar dependências indiscriminadamente;
- não substituir tecnologia funcionando por preferência pessoal.

Caso encontre problema fora do escopo:

- registrar;
- informar;
- não corrigir silenciosamente se houver risco de ampliar a mudança.

## Qualidade E Simplicidade

Priorizar nesta ordem:

1. correção;
2. aderência aos requisitos acadêmicos;
3. clareza;
4. testabilidade;
5. simplicidade;
6. manutenibilidade;
7. desempenho, quando houver necessidade demonstrável.

Evitar abstrações prematuras.

Código deve ser compreensível por outros integrantes da equipe.

## Git

Não realizar commits, pushes, merges, criação de tags ou alteração de histórico automaticamente sem solicitação explícita.

Não atribuir commits a outro integrante.

Não alterar configuração de identidade Git para simular participação.

Preservar histórico incremental das aulas.

Quando solicitado a sugerir commits, propor commits pequenos, coerentes e semanticamente relacionados.

## Procedimento Obrigatório Antes De Codificar

Antes de modificar código:

1. identificar o requisito atual;
2. ler a constituição;
3. consultar ADRs relacionados;
4. inspecionar implementação existente;
5. identificar testes existentes;
6. avaliar impacto em contratos;
7. avaliar impacto em publisher/consumer;
8. verificar impacto em documentação;
9. identificar dúvidas de domínio;
10. somente então implementar.

Se existir conflito entre requisito novo e arquitetura existente, não mascarar o conflito.
