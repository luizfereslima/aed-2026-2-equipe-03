package br.pucminas.aed.vendaingressospainel.controller;

import br.pucminas.aed.vendaingressospainel.domain.JanelaVendasVO;
import br.pucminas.aed.vendaingressospainel.service.PainelVendasService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/painel-vendas")
public class PainelVendasController {

    private final PainelVendasService painelVendasService;

    public PainelVendasController(PainelVendasService painelVendasService) {
        this.painelVendasService = painelVendasService;
    }

    @GetMapping("/janelas")
    public List<JanelaVendasVO> janelas() {
        return painelVendasService.janelas();
    }
}
