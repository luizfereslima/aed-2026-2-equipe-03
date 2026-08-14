package br.pucminas.aed.vendaingressos.service;

import br.pucminas.aed.vendaingressos.controller.CompraIngressoVO;
import br.pucminas.aed.vendaingressos.controller.SolicitarCompraVO;
import br.pucminas.aed.vendaingressos.domain.IngressoEmitidoEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IngressoService {

    private final AssentoService assentoService;
    private final PagamentoService pagamentoService;
    private final PublicacaoIngressoService publicacaoIngressoService;

    public IngressoService(
            AssentoService assentoService,
            PagamentoService pagamentoService,
            PublicacaoIngressoService publicacaoIngressoService
    ) {
        this.assentoService = assentoService;
        this.pagamentoService = pagamentoService;
        this.publicacaoIngressoService = publicacaoIngressoService;
    }

    public CompraIngressoVO solicitarCompra(SolicitarCompraVO solicitacao) {
        assentoService.reservar(
                solicitacao.getEventoComercialId(),
                solicitacao.getSetorId(),
                solicitacao.getAssentoId()
        );

        String vendaId = UUID.randomUUID().toString();
        if (!pagamentoService.autorizar(vendaId)) {
            throw new IllegalStateException("pagamento não autorizado");
        }

        String ingressoId = UUID.randomUUID().toString();
        IngressoEmitidoEvent evento = new IngressoEmitidoEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(ZoneOffset.UTC),
                ingressoId,
                vendaId,
                solicitacao.getEventoComercialId(),
                solicitacao.getSetorId(),
                solicitacao.getAssentoId()
        );

        publicacaoIngressoService.publicar(evento);

        return new CompraIngressoVO(vendaId, ingressoId, "EMISSAO_SOLICITADA");
    }
}
