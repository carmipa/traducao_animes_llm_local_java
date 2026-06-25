package org.traducao.projeto.legendas.application.strategy;

import org.traducao.projeto.legendas.domain.FaixaLegenda;
import org.traducao.projeto.legendas.domain.FormatoLegenda;

import java.util.List;
import java.util.Optional;

public interface ExtratorStrategy {
    boolean suporta(FormatoLegenda formato);
    Optional<FaixaLegenda> selecionarMelhorFaixa(List<FaixaLegenda> faixasDisponiveis);
}
