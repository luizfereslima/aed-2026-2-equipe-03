package br.pucminas.aed.vendaingressosconsumer.service;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngressoEmitidoRepository extends JpaRepository<IngressoEmitidoVO, Long> {

    long countByEventoId(String eventoId);

    java.util.Optional<IngressoEmitidoVO> findByIngressoId(String ingressoId);
}
