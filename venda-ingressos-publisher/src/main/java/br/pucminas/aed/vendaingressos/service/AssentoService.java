package br.pucminas.aed.vendaingressos.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssentoService {

    public void reservar(String eventoComercialId, String setorId, String assentoId) {
        if (!StringUtils.hasText(eventoComercialId)
                || !StringUtils.hasText(setorId)
                || !StringUtils.hasText(assentoId)) {
            throw new IllegalArgumentException("eventoComercialId, setorId e assentoId são obrigatórios");
        }
    }
}
