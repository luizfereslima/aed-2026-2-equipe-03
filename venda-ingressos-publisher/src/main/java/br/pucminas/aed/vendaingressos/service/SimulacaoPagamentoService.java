package br.pucminas.aed.vendaingressos.service;

import org.springframework.stereotype.Service;

@Service
public class SimulacaoPagamentoService implements PagamentoService {

    @Override
    public boolean autorizar(String vendaId) {
        return true;
    }
}
