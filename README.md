# Equipe 03

Projeto acadêmico da disciplina Arquitetura Reativa e Event-Driven (AED) da PUC Minas.

## Integrantes

```text
Luiz Felipe Dias Cardoso Feres Lima — 254124
TODO_EQUIPE: nome completo — matrícula
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
 ↓
Publisher
 ↓
Kafka
 ↓
Consumer idempotente
 ↓
Banco H2
```

O `venda-ingressos-publisher` recebe uma solicitação HTTP, executa o fluxo inicial de venda de ingresso com gateway de pagamento simulado e publica o evento `IngressoEmitidoEvent`.

O `venda-ingressos-consumer` consome o evento, verifica idempotência por `eventoId` e registra a projeção/auditoria de ingressos emitidos em banco relacional.

Publisher e consumer são projetos Maven independentes. Não existe POM pai compartilhado nem módulo compartilhado de contrato.

## Tecnologias

- Java 21
- Spring Boot
- Maven
- Apache Kafka
- Docker Compose
- H2 Database no consumer

O H2 foi escolhido como banco relacional simples e compatível com Spring Boot para demonstrar atomicidade entre efeito de negócio e memória de deduplicação. Ele inicia embutido junto com o consumer. No Docker Compose, os dados ficam no volume `consumer-data`.

O Kafka roda em modo KRaft, sem ZooKeeper.

## Estrutura do repositório

```text
.
├── AGENTS.md
├── README.md
├── docker-compose.yml
├── docs
│   ├── IA.md
│   ├── adr
│   │   └── ADR-002-dominio-do-projeto.md
│   ├── entregas
│   │   └── aula-02.md
│   └── identificacao-canvas.md
├── venda-ingressos-consumer
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

O publisher ficará disponível em `http://localhost:8080`.

O consumer ficará disponível internamente e escutará o Kafka usando o broker `kafka:9092`.

Também é possível executar as aplicações fora do Docker. Nesse caso, suba apenas o Kafka:

```bash
docker compose up -d kafka
```

Em um terminal, inicie o consumer:

```bash
cd venda-ingressos-consumer
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

## Como verificar idempotência

O teste automatizado do consumer entrega o mesmo `IngressoEmitidoEvent` três vezes e valida que:

- a projeção de ingresso emitido é registrada uma única vez;
- a tabela de deduplicação contém um único registro para o `eventoId`.

Execute:

```bash
cd venda-ingressos-consumer
mvn test -Dtest=IngressoServiceTest
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
git tag entrega-aula-02
git push origin entrega-aula-02
```

Não crie a tag antes da validação final da equipe.
