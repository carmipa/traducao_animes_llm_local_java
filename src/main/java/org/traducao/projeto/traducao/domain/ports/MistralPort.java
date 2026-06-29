package org.traducao.projeto.traducao.domain.ports;

import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.StatusLlm;
import org.traducao.projeto.traducao.domain.TraducaoLote;

import java.util.List;
import java.util.Optional;

public interface MistralPort {
    TraducaoLote traduzir(Lote lote);

    /**
     * Verifica, antes de iniciar a tradução, se o servidor LLM local está
     * online e se o modelo configurado está efetivamente carregado em
     * memória — evita descobrir isso só depois de várias tentativas/timeouts
     * já no meio da tradução do primeiro episódio.
     */
    StatusLlm verificarDisponibilidade();

    /**
     * Revisa uma fala já traduzida, corrigindo concordância de gênero/pronomes.
     * Retorna vazio se o LLM falhar ou a resposta for inválida.
     */
    Optional<String> revisarConcordancia(
        String originalInglesMascarado,
        String traducaoPtMascarada,
        List<String> problemasDetectados
    );

    /**
     * Retraduz uma fala cuja tradução existente ficou com resíduo em inglês,
     * incompleta ou alucinada, usando o prompt completo (lore + regras) do
     * contexto ativo. Retorna vazio se o LLM falhar ou a resposta for inválida.
     */
    Optional<String> corrigirTraducao(
        String originalInglesMascarado,
        String traducaoPtMascarada,
        String motivoDetectado
    );
}
