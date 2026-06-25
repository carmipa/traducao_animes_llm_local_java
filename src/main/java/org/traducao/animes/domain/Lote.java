package org.traducao.animes.domain;

import java.util.List;

public record Lote(
    int idLote,
    List<String> linhasOriginais
) {
}
