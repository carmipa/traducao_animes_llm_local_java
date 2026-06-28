package org.traducao.projeto.traducao.contexto.danmachi;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoDanMachiS5 implements ProvedorContexto {
    private static final String PROMPT = ContextoPrompt.montar("DanMachi (Season 5)", "- Obra: DanMachi Season 5 (Goddess of Fertility Arc).\n- Personagens: Bell Cranel, Freya, Syr Flover, Ottar, Hestia, Mia.");
    @Override public String getId() { return "danmachi_s5"; }
    @Override public String getNomeExibicao() { return "DanMachi (Season 5)"; }
    @Override public String obterPromptSistema() { return PROMPT; }
}
