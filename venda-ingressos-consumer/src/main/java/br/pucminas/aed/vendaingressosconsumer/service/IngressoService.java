package br.pucminas.aed.vendaingressosconsumer.service;

import br.pucminas.aed.vendaingressosconsumer.domain.IngressoEmitidoEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
