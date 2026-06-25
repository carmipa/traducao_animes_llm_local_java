package org.traducao.projeto.animes.domain.legenda;

import java.util.List;

public record DocumentoLegenda(
    String cabecalho,
    List<EventoLegenda> eventos,
    String quebraDeLinha,
    boolean comBom
) {
}
