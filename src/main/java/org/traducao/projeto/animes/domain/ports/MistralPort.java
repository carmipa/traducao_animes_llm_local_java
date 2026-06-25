package org.traducao.projeto.animes.domain.ports;

import org.traducao.projeto.animes.domain.Lote;
import org.traducao.projeto.animes.domain.TraducaoLote;

public interface MistralPort {
    TraducaoLote traduzir(Lote lote);
}
