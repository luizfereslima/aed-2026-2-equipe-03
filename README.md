# Equipe 03

Projeto acadêmico da disciplina Arquitetura Reativa e Event-Driven (AED) da PUC Minas.

## Integrantes

```text
Luiz Felipe Dias Cardoso Feres Lima — 254124
Gabriel Santiago Silva - 258220
Gabriel Grapeggia Ceola - 255596
Daniel da Silveira Moreira - 255927
TODO_EQUIPE: nome completo — matrícula
```

## Líder

```text
Luiz Felipe Dias Cardoso Feres Lima — 254124
```

## Domínio em uma frase

Plataforma de venda de ingressos que reserva disponibilidade, processa pagamento por meio de um gateway externo e emite ingressos após a confirmação da venda.

## Arquitetura

```text
HTTP
 ↓                             503 + Retry-After quando o produtor não tem espaço
Publisher
 ↓
Kafka — ingressos.ingresso-emitido.v1 (3 partições, chave eventoComercialId)
 ├──────────────────────────────┬──────────────────────────────┐
 ↓                              ↓
Consumer idempotente           Painel de vendas
grupo: venda-ingressos-consumer  grupo: venda-ingressos-painel
concurrency 3                   concurrency 1
 ↓                              ↓
Banco H2                       Janelas de 5 min em memória
 ↓ o que falha                  ↓ o que falha
...v1.dlt-consumer             ...v1.dlt-painel
```

O `venda-ingressos-publisher` recebe uma solicitação HTTP, executa o fluxo inicial de venda de ingresso com gateway de pagamento simulado e publica o evento `IngressoEmitidoEvent`.

O `venda-ingressos-consumer` consome o evento, verifica idempotência por `eventoId` e registra a projeção/auditoria de ingressos emitidos em banco relacional.

O `venda-ingressos-painel` consome o **mesmo** tópico em um **grupo de consumidores próprio** e agrega quantos ingressos foram emitidos por evento comercial a cada cinco minutos, pela hora de ocorrência do fato. Os dois consumidores recebem todas as mensagens; nenhum tira mensagem do outro.

Cada consumidor tem um **tópico de descarte próprio**. A mensagem que não pode ser processada
— carga malformada, ou evento sem os campos de que aquele consumidor depende — é encaminhada
para lá em vez de travar a partição ou sumir. Falha transitória, ao contrário, é repetida com
espera crescente até 30 segundos. O porquê da distinção está no
[ADR-004](docs/adr/ADR-004-backpressure-e-falha-no-consumo.md).

O contrato do evento está em [docs/contrato.md](docs/contrato.md).

As três aplicações são projetos Maven independentes. Não existe POM pai compartilhado nem módulo compartilhado de contrato.

## Tecnologias

- Java 21
- Spring Boot
- Maven
- Apache Kafka
- Docker Compose
- H2 Database no consumer

O H2 foi escolhido como banco relacional simples e compatível com Spring Boot para demonstrar atomicidade entre efeito de negócio e memória de deduplicação. Ele inicia embutido junto com o consumer. No Docker Compose, os dados ficam no volume `consumer-data`.

O Kafka roda em modo KRaft, sem ZooKeeper. A imagem vem de `bitnamilegacy/kafka`, e não de `bitnami/kafka`: a Bitnami retirou o catálogo público do Docker Hub em 2025 e moveu as imagens existentes para o repositório legado. É a mesma imagem 3.7.

## Estrutura do repositório

```text
.
├── AGENTS.md
├── README.md
├── docker-compose.yml
├── docs
│   ├── IA.md
│   ├── adr
│   │   ├── ADR-002-dominio-do-projeto.md
│   │   ├── ADR-003-agregacao-por-janela.md
│   │   └── ADR-004-backpressure-e-falha-no-consumo.md
│   ├── contrato.md
│   ├── entregas
│   │   ├── aula-02.md
│   │   ├── aula-03.md
│   │   └── aula-04.md
│   └── identificacao-canvas.md
├── venda-ingressos-consumer
├── venda-ingressos-painel
└── venda-ingressos-publisher
```

## Como executar em máquina limpa

Pré-requisitos:

- Java 21
- Maven
- Docker com Docker Compose

Suba Kafka, publisher e consumer:

```bash
docker compose up -d --build
```

Depois do primeiro build, use:

```bash
docker compose up -d
```

Se você já tinha subido o projeto antes da Aula 04, o tópico existente foi criado com uma
partição só. As aplicações aumentam o número de partições na subida, mas **as mensagens
antigas não mudam de lugar** — elas continuam na partição 0. Para um começo limpo:

```bash
docker compose down -v
```

O publisher ficará disponível em `http://localhost:8080`.

O consumer ficará disponível em `http://localhost:8081` e o painel de vendas em `http://localhost:8082`. Os dois escutam o Kafka usando o broker `kafka:9092`.

Também é possível executar as aplicações fora do Docker. Nesse caso, suba apenas o Kafka:

```bash
docker compose up -d kafka
```

Em um terminal, inicie o consumer:

```bash
cd venda-ingressos-consumer
mvn spring-boot:run
```

Em outro terminal, inicie o painel:

```bash
cd venda-ingressos-painel
mvn spring-boot:run
```

Em outro terminal, inicie o publisher:

```bash
cd venda-ingressos-publisher
mvn spring-boot:run
```

## Como testar

Execute os testes de cada aplicação:

```bash
cd venda-ingressos-publisher
mvn test
```

```bash
cd venda-ingressos-consumer
mvn test
```

```bash
cd venda-ingressos-painel
mvn test
```

## Como disparar um evento

Com Kafka, consumer e publisher em execução:

```bash
curl -i -X POST http://localhost:8080/vendas-ingressos \
  -H "Content-Type: application/json" \
  -d '{
    "eventoComercialId": "evento-comercial-001",
    "setorId": "setor-a",
    "assentoId": "assento-a-10"
  }'
```

A API deve responder `202 Accepted`. O evento publicado usa CloudEvents 1.0 em modo binário.

## Como subir o painel de vendas

O `venda-ingressos-painel` é o consumidor novo da Aula 03. Ele lê o mesmo tópico do `venda-ingressos-consumer`, mas com o grupo de consumidores `venda-ingressos-painel`, e responde à pergunta: **quantos ingressos foram emitidos para cada evento comercial a cada cinco minutos?**

Com Docker Compose ele já sobe junto com o resto:

```bash
docker compose up -d --build
```

Para subir só o painel, com o Kafka já em execução:

```bash
docker compose up -d venda-ingressos-painel
```

Fora do Docker, com o Kafka em execução:

```bash
cd venda-ingressos-painel
mvn spring-boot:run
```

O resultado da agregação aparece em dois lugares. No endpoint:

```bash
curl -s http://localhost:8082/painel-vendas/janelas
```

```json
[
  {
    "eventoComercialId": "evento-comercial-001",
    "inicio": "2026-08-16T10:05:00Z",
    "fim": "2026-08-16T10:10:00Z",
    "ingressosEmitidos": 3,
    "fechada": false
  }
]
```

E no log, quando a janela fecha:

```bash
docker compose logs -f venda-ingressos-painel
```

```text
JANELA FECHADA eventoComercialId=evento-comercial-001 inicio=2026-08-16T10:05Z fim=2026-08-16T10:10Z ingressos=3
```

As janelas são alinhadas pelo relógio em `:00`, `:05`, `:10`… (UTC), e não pela hora em que o processo subiu. O relógio usado é o de **ocorrência** — o `ocorridoEm` de dentro do evento. O porquê está em [docs/entregas/aula-03.md](docs/entregas/aula-03.md) e em [docs/adr/ADR-003](docs/adr/ADR-003-agregacao-por-janela.md).

Um evento atrasado sempre entra na janela dele, mesmo depois de a janela ter sido fechada; nesse caso o painel loga `JANELA CORRIGIDA` e o endpoint passa a mostrar o valor corrigido.

O estado das janelas vive em memória: reiniciar o processo zera as janelas, e a reconstrução é reprocessar o tópico desde o começo.

## Como ver os dois consumidores processando ao mesmo tempo

Com tudo em execução, dispare algumas compras:

```bash
for i in 1 2 3; do curl -s -o /dev/null -X POST http://localhost:8080/vendas-ingressos -H "Content-Type: application/json" -d "{\"eventoComercialId\":\"evento-comercial-001\",\"setorId\":\"setor-a\",\"assentoId\":\"assento-a-$i\"}"; done
```

O `venda-ingressos-consumer` grava a projeção:

```bash
docker compose logs venda-ingressos-consumer
```

E o `venda-ingressos-painel` conta, no mesmo tópico e no mesmo momento:

```bash
docker compose logs venda-ingressos-painel | grep "Evento recebido"
```

```text
Evento recebido no painel particao=1 offset=0 eventoId=14eb02d1-... ocorridoEm=2026-08-22T22:28:05.124705892Z
Evento recebido no painel particao=1 offset=1 eventoId=a5b86717-... ocorridoEm=2026-08-22T22:28:05.308352064Z
Evento recebido no painel particao=1 offset=2 eventoId=8a941bb6-... ocorridoEm=2026-08-22T22:28:05.325035498Z
```

As três caem na **mesma** partição porque têm a mesma chave, o `eventoComercialId`. Qual das
três é decidido pelo hash da chave, então o número pode ser outro na sua máquina.

A prova direta é perguntar ao próprio Kafka:

```bash
docker exec aed-kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
```

```text
GROUP                     TOPIC                          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  0          0               0               0
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  1          3               3               0
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  2          0               0               0
venda-ingressos-painel    ingressos.ingresso-emitido.v1  0          0               0               0
venda-ingressos-painel    ingressos.ingresso-emitido.v1  1          3               3               0
venda-ingressos-painel    ingressos.ingresso-emitido.v1  2          0               0               0
```

Os dois grupos leram as **mesmas** três mensagens, cada um com o próprio offset e lag zero. Se compartilhassem o grupo, cada mensagem iria para um só deles e a soma dos dois é que daria três.

## Como demonstrar backpressure

O painel apura em memória e é rápido demais para acumular fila: sob rajada normal o lag fica
em zero e não há o que mostrar. Por isso existe um freio de demonstração, desligado por
padrão.

Ligue o freio em `docker-compose.yml`, no serviço `venda-ingressos-painel`:

```yaml
      APP_PAINEL_ATRASO_SIMULADO: PT2S
```

Recrie só o painel:

```bash
docker compose up -d --force-recreate venda-ingressos-painel
```

Dispare uma rajada de 60 compras, distribuídas entre três shows:

```bash
for i in $(seq 1 60); do curl -s -o /dev/null -X POST http://localhost:8080/vendas-ingressos -H "Content-Type: application/json" -d "{\"eventoComercialId\":\"evento-comercial-00$((i % 3 + 1))\",\"setorId\":\"setor-a\",\"assentoId\":\"assento-a-$i\"}"; done
```

E acompanhe o lag dos dois grupos:

```bash
docker exec aed-kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
```

```text
GROUP                     TOPIC                          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  0          20              20              0
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  1          20              20              0
venda-ingressos-consumer  ingressos.ingresso-emitido.v1  2          20              20              0
venda-ingressos-painel    ingressos.ingresso-emitido.v1  0          6               20              14
venda-ingressos-painel    ingressos.ingresso-emitido.v1  1          5               20              15
venda-ingressos-painel    ingressos.ingresso-emitido.v1  2          6               20              14
```

É essa a imagem do backpressure: o `venda-ingressos-consumer` acompanha a produção e fica com
lag zero; o painel, freado, deixa a fila **no broker** — não em memória do processo, e não no
produtor. Repetindo o comando a cada poucos segundos, o lag do painel cai sozinho até zerar.
Nenhuma mensagem é perdida, e ninguém precisou avisar o produtor para desacelerar.

### A partição quente, agora visível

A rajada acima usa três eventos comerciais justamente para que as três partições apareçam
ocupadas. Repita a rajada com **um** `eventoComercialId` só e compare:

```bash
for i in $(seq 1 60); do curl -s -o /dev/null -X POST http://localhost:8080/vendas-ingressos -H "Content-Type: application/json" -d "{\"eventoComercialId\":\"evento-comercial-001\",\"setorId\":\"setor-a\",\"assentoId\":\"assento-b-$i\"}"; done
```

As 60 mensagens caem todas na **mesma** partição, porque a chave é o `eventoComercialId` e a
chave decide a partição. É exatamente a partição quente que o
[ADR-002](docs/adr/ADR-002-dominio-do-projeto.md) previu: o pico de abertura de vendas de um
show grande não se distribui, por definição. Passar de uma para três partições não resolve
isso — apenas torna o problema **mensurável**, que era o pré-requisito registrado lá. A
mitigação avaliada (chave composta evento comercial + setor, trocando a ordem garantida pela
ordem por setor) continua parada, aguardando esta medição.

Ao terminar, volte o freio para `PT0S` e recrie o painel.

### Backpressure no produtor

Quando o buffer do produtor Kafka enche e a espera por espaço (`max.block.ms`) se esgota, o
publisher **recusa** a solicitação com `503 Service Unavailable` e `Retry-After`, em vez de
responder `202 Accepted` para um evento que não entrou em lugar nenhum. É o mesmo empurrão
para trás, agora chegando a quem gera a carga. Para provocá-lo é preciso derrubar o Kafka
(`docker compose stop kafka`) e disparar compras até o buffer de 32 MB encher.

## Como verificar o tópico de descarte

Cada consumidor tem o seu, porque os dois falham por motivos diferentes sobre a mesma
mensagem. Publique uma carga malformada direto no tópico:

```bash
docker exec -i aed-kafka /opt/bitnami/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic ingressos.ingresso-emitido.v1
```

Cole uma linha por vez e encerre com `Ctrl+D`:

```text
isto nao e json
{"eventoId":"teste-sem-ocorridoem","eventoComercialId":"evento-comercial-001"}
```

A primeira linha não desserializa em ninguém, e vai para os **dois** tópicos de descarte. A
segunda é JSON válido e mostra a diferença entre os consumidores: o painel a recusa, porque
sem `ocorridoEm` não existe janela a que ela pertença; o `venda-ingressos-consumer` a aceita,
porque não precisa daquele campo para manter a projeção.

Veja o que caiu em cada um:

```bash
docker exec aed-kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ingressos.ingresso-emitido.v1.dlt-painel --from-beginning --property print.headers=true
```

```bash
docker exec aed-kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ingressos.ingresso-emitido.v1.dlt-consumer --from-beginning --property print.headers=true
```

Os cabeçalhos `kafka_dlt-*` que o Spring acrescenta dizem o tópico, a partição, o offset e a
exceção de origem. A carga que falhou na desserialização chega como texto base64 — a
consequência aceita está registrada no [ADR-004](docs/adr/ADR-004-backpressure-e-falha-no-consumo.md).

O importante é o que **não** acontece: a partição não trava, os consumidores seguem
processando as mensagens seguintes, e nada é descartado em silêncio.

O tópico de descarte não tem consumidor automático. Reprocessar é uma decisão manual, tomada
depois de olhar o que está lá.

## Como verificar idempotência

O teste automatizado do consumer entrega o mesmo `IngressoEmitidoEvent` três vezes e valida que:

- a projeção de ingresso emitido é registrada uma única vez;
- a tabela de deduplicação contém um único registro para o `eventoId`.

Execute:

```bash
cd venda-ingressos-consumer
mvn test -Dtest=IngressoServiceTest
```

## Como verificar o contrato no fio

O contrato escrito está em [docs/contrato.md](docs/contrato.md). Ele é verificado por testes automatizados no publisher, sem depender de inspeção manual do tópico.

`IngressoEmitidoEventSerializacaoTest` serializa o `IngressoEmitidoEvent` com o mesmo `JsonSerializer` configurado em `application.yml` e valida que:

- `ocorridoEm` sai como texto ISO-8601, e não como número epoch;
- o payload contém exatamente os sete campos do contrato.

`PublicacaoIngressoServiceTest` lê `app.kafka.*` do próprio `application.yml` e valida que:

- os cabeçalhos `ce_specversion`, `ce_id`, `ce_source` e `ce_type` saem preenchidos;
- `ce_id` é o `eventoId`, e não o `ingressoId` ou o `vendaId`;
- a chave de partição é o `eventoComercialId`, conforme o ADR-002;
- o `ce_type` publicado tem grafia única entre o código, o ADR-002 e o `docs/contrato.md`.

No painel, `IngressoEmitidoEventTest` desserializa a carga completa dos sete campos na classe de três campos daquele módulo, exercendo a regra de compatibilidade FULL declarada no contrato:

```bash
cd venda-ingressos-painel
mvn test -Dtest=IngressoEmitidoEventTest
```

Execute:

```bash
cd venda-ingressos-publisher
mvn test -Dtest=IngressoEmitidoEventSerializacaoTest,PublicacaoIngressoServiceTest
```

## Como verificar mensagens no Kafka

Com o Docker Compose ativo:

```bash
docker exec -it aed-kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic ingressos.ingresso-emitido.v1 \
  --from-beginning
```

## Como parar

```bash
docker compose down
```

Para remover também os volumes do Kafka e do H2:

```bash
docker compose down -v
```

## Configuração do Git

Cada integrante deve configurar a própria identidade antes de fazer commits:

```bash
git config user.name "SUA_MATRICULA"
git config user.email "EMAIL_CADASTRADO_NO_GITHUB"
```

## Tag de entrega

Crie a tag somente quando a equipe validar a entrega:

```bash
git tag -a entrega-aula-04 -m "Etapa 3: backpressure e politica de falha"
```

```bash
git push origin entrega-aula-04
```

Não crie a tag antes da validação final da equipe.
