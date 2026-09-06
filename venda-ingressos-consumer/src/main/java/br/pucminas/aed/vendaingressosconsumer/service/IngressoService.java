package br.pucminas.aed.vendaingressosconsumer.service;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
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
        exigirEventoId(evento);

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

    /**
     * Sem {@code eventoId} não existe chave de deduplicação, então não há como ser idempotente
     * — e insistir não conserta a carga. A exceção está classificada como não repetível no
     * {@code KafkaConfig}, e leva a mensagem direto ao tópico de descarte.
     */
    private void exigirEventoId(IngressoEmitidoEvent evento) {
        if (!StringUtils.hasText(evento.getEventoId())) {
            throw new IllegalArgumentException(
                    "eventoId é obrigatório no contrato e é a chave de deduplicação; evento sem ele não é processável"
            );
        }
    }
}
