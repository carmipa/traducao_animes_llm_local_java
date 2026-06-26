package org.traducao.projeto.traducao.domain.ports;

import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.TraducaoLote;

public interface MistralPort {
    TraducaoLote traduzir(Lote lote);
}
