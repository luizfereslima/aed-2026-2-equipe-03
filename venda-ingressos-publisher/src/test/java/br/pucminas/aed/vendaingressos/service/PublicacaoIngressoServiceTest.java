package br.pucminas.aed.vendaingressos.service;

import static br.pucminas.aed.vendaingressos.service.PublicacaoIngressoService.CE_ID;
import static br.pucminas.aed.vendaingressos.service.PublicacaoIngressoService.CE_SOURCE;
import static br.pucminas.aed.vendaingressos.service.PublicacaoIngressoService.CE_SPECVERSION;
import static br.pucminas.aed.vendaingressos.service.PublicacaoIngressoService.CE_TIME;
import static br.pucminas.aed.vendaingressos.service.PublicacaoIngressoService.CE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucminas.aed.vendaingressos.domain.IngressoEmitidoEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;

class PublicacaoIngressoServiceTest {

    private static final Path ADR_DOMINIO = Path.of("..", "docs", "adr", "ADR-002-dominio-do-projeto.md");
    private static final Path CONTRATO = Path.of("..", "docs", "contrato.md");

    @Test
    void devePublicarComCloudEventsBinarioEChaveEventoComercialId() {
        Properties configuracao = configuracaoDaAplicacao();
        String topico = configuracao.getProperty("app.kafka.topico-ingresso-emitido");
        String ceSource = configuracao.getProperty("app.kafka.ce-source");
        String ceType = configuracao.getProperty("app.kafka.ce-type-ingresso-emitido");

        KafkaTemplate<String, IngressoEmitidoEvent> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        PublicacaoIngressoService service = new PublicacaoIngressoService(kafkaTemplate, topico, ceSource, ceType);
        IngressoEmitidoEvent evento = eventoDeExemplo();

        service.publicar(evento);

        ArgumentCaptor<ProducerRecord<String, IngressoEmitidoEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, IngressoEmitidoEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo(topico);
        assertThat(record.key())
                .as("a chave de partição é a unidade cuja ordem o negócio exige, conforme o ADR-002")
                .isEqualTo("evento-comercial-001");
        assertThat(header(record, CE_SPECVERSION)).isEqualTo("1.0");
        assertThat(header(record, CE_ID))
                .as("ce_id deve ser a identidade do fato, não a da entidade de negócio")
                .isEqualTo(evento.getEventoId());
        assertThat(header(record, CE_SOURCE)).isNotBlank().isEqualTo(ceSource);
        assertThat(header(record, CE_TYPE)).isNotBlank().isEqualTo(ceType);
        assertThat(header(record, CE_TIME)).isEqualTo("2026-08-16T10:15:30Z");
    }

    @Test
    void deveManterGrafiaUnicaDoCeTypeEntreConfiguracaoEAdr() throws Exception {
        String ceType = configuracaoDaAplicacao().getProperty("app.kafka.ce-type-ingresso-emitido");

        exigirDocumentacaoDoRepositorio(ADR_DOMINIO);
        assertThat(Files.readString(ADR_DOMINIO, StandardCharsets.UTF_8))
                .as("o ce_type publicado deve ter uma grafia só em código e documentação")
                .contains(ceType);
    }

    @Test
    void deveManterGrafiaUnicaDoCeTypeEntreConfiguracaoEContrato() throws Exception {
        Properties configuracao = configuracaoDaAplicacao();
        String ceType = configuracao.getProperty("app.kafka.ce-type-ingresso-emitido");
        String topico = configuracao.getProperty("app.kafka.topico-ingresso-emitido");

        exigirDocumentacaoDoRepositorio(CONTRATO);
        String contrato = Files.readString(CONTRATO, StandardCharsets.UTF_8);

        assertThat(contrato)
                .as("o tipo do evento no contrato precisa ser o mesmo que o código publica")
                .contains(ceType);
        assertThat(contrato)
                .as("o tópico documentado precisa ser o mesmo que o código publica")
                .contains(topico);
        assertThat(contrato)
                .as("a chave de partição documentada precisa ser a mesma que o código usa")
                .contains("eventoComercialId");
    }

    /**
     * Os testes de coerência entre código e documentação só fazem sentido quando a suíte roda
     * de dentro do repositório. A imagem Docker do publisher é construída com a pasta do módulo
     * como contexto, e ali {@code ../docs} não existe: o módulo é independente de propósito.
     * Nesse caso o teste é pulado, e não falsamente aprovado — o {@code mvn test} documentado
     * no README continua sendo o lugar onde a divergência aparece.
     */
    private void exigirDocumentacaoDoRepositorio(Path documento) {
        assumeTrue(
                Files.exists(documento),
                () -> documento + " não está acessível; suíte rodando fora do repositório (build da imagem)"
        );
    }

    private Properties configuracaoDaAplicacao() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        yaml.afterPropertiesSet();
        return yaml.getObject();
    }

    private IngressoEmitidoEvent eventoDeExemplo() {
        return new IngressoEmitidoEvent(
                "evento-001",
                OffsetDateTime.parse("2026-08-16T10:15:30Z"),
                "ingresso-001",
                "venda-001",
                "evento-comercial-001",
                "setor-a",
                "assento-a-10"
        );
    }

    private String header(ProducerRecord<String, IngressoEmitidoEvent> record, String nome) {
        return new String(record.headers().lastHeader(nome).value(), StandardCharsets.UTF_8);
    }
}
