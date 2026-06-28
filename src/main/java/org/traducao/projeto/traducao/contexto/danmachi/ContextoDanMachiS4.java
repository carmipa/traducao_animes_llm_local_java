package org.traducao.projeto.traducao.contexto.danmachi;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoDanMachiS4 implements ProvedorContexto {
    private static final String PROMPT = ContextoPrompt.montar("DanMachi (Season 4)", "- Obra: DanMachi Season 4 (Deep Floors / Labyrinth Arc).\n- Personagens: Bell Cranel, Ryuu Lion, Hestia, Cassandra, Marie.");
    @Override public String getId() { return "danmachi_s4"; }
    @Override public String getNomeExibicao() { return "DanMachi (Season 4)"; }
    @Override public String obterPromptSistema() { return PROMPT; }
}
