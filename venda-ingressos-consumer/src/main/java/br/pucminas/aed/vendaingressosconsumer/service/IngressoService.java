package br.pucminas.aed.vendaingressosconsumer.service;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
import br.pucminas.aed.vendaingressosconsumer.domain.IngressoInvalidadoEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IngressoService {

    private final EventoProcessadoRepository eventoProcessadoRepository;
    private final IngressoEmitidoRepository ingressoEmitidoRepository;

    public IngressoService(
            EventoProcessadoRepository eventoProcessadoRepository,
            IngressoEmitidoRepository ingressoEmitidoRepository
    ) {
        this.eventoProcessadoRepository = eventoProcessadoRepository;
        this.ingressoEmitidoRepository = ingressoEmitidoRepository;
    }

    @Transactional
    public void processar(IngressoEmitidoEvent evento) {
        validarEventoEmitido(evento);

        if (eventoProcessadoRepository.existsById(evento.getEventoId())) {
            return;
        }

        ingressoEmitidoRepository.save(new IngressoEmitidoVO(
                evento.getEventoId(),
                evento.getIngressoId(),
                evento.getVendaId(),
                evento.getEventoComercialId(),
                evento.getOcorridoEm()
        ));
        eventoProcessadoRepository.save(new EventoProcessadoVO(
                evento.getEventoId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
    }

    @Transactional
    public void invalidar(IngressoInvalidadoEvent evento) {
        validarEventoInvalidado(evento);
        if (eventoProcessadoRepository.existsById(evento.getEventoId())) {
            return;
        }

        IngressoEmitidoVO ingresso = ingressoEmitidoRepository.findByIngressoId(evento.getIngressoId())
                .orElseThrow(() -> new IllegalArgumentException("ingresso emitido não encontrado"));
        ingresso.invalidar(evento.getMotivo(), evento.getOcorridoEm());
        ingressoEmitidoRepository.save(ingresso);
        eventoProcessadoRepository.save(new EventoProcessadoVO(
                evento.getEventoId(), OffsetDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Sem {@code eventoId} não existe chave de deduplicação, então não há como ser idempotente
     * — e insistir não conserta a carga. A exceção está classificada como não repetível no
     * {@code KafkaConfig}, e leva a mensagem direto ao tópico de descarte.
     */
    private void validarEventoEmitido(IngressoEmitidoEvent evento) {
        if (evento == null) {
            throw new IllegalArgumentException("evento de emissão não pode ser nulo");
        }
        exigirTexto(evento.getEventoId(), "eventoId");
        exigirTexto(evento.getIngressoId(), "ingressoId");
        exigirTexto(evento.getVendaId(), "vendaId");
        exigirTexto(evento.getEventoComercialId(), "eventoComercialId");
        if (evento.getOcorridoEm() == null) {
            throw new IllegalArgumentException("ocorridoEm é obrigatório no contrato");
        }
    }

    private void validarEventoInvalidado(IngressoInvalidadoEvent evento) {
        if (evento == null) {
            throw new IllegalArgumentException("evento de invalidação não pode ser nulo");
        }
        exigirTexto(evento.getEventoId(), "eventoId");
        exigirTexto(evento.getIngressoId(), "ingressoId");
        exigirTexto(evento.getVendaId(), "vendaId");
        exigirTexto(evento.getEventoComercialId(), "eventoComercialId");
        exigirTexto(evento.getMotivo(), "motivo");
        if (evento.getOcorridoEm() == null) {
            throw new IllegalArgumentException("ocorridoEm é obrigatório no contrato");
        }
    }

    private void exigirTexto(String valor, String nomeCampo) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalArgumentException(nomeCampo + " é obrigatório no contrato");
        }
    }
}
