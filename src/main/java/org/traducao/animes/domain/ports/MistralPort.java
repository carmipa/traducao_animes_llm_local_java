package org.traducao.animes.domain.ports;

import org.traducao.animes.domain.Lote;
import org.traducao.animes.domain.TraducaoLote;

public interface MistralPort {
    TraducaoLote traduzir(Lote lote);
}
