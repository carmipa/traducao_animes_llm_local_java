package org.traducao.projeto.traducao.domain.ports;

import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.StatusLlm;
import org.traducao.projeto.traducao.domain.TraducaoLote;

public interface MistralPort {
    TraducaoLote traduzir(Lote lote);

    /**
     * Verifica, antes de iniciar a tradução, se o servidor LLM local está
     * online e se o modelo configurado está efetivamente carregado em
     * memória — evita descobrir isso só depois de várias tentativas/timeouts
     * já no meio da tradução do primeiro episódio.
     */
    StatusLlm verificarDisponibilidade();
}
