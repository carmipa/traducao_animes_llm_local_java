package org.traducao.projeto.traducao.contexto.gundam;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoGundamNT implements ProvedorContexto {

    private static final String LORE = """
        - Obra: Mobile Suit Gundam NT (Narrative).
        - Personagens: Jona Basta (homem), Michelle Luio (mulher), Rita Bernal (mulher), Zoltan Akkanen (homem), Iamesh Ormsbyl (homem).
        - Mechas / Termos: RX-9 Narrative Gundam, RX-0 Unicorn Gundam 03 Phenex, NZ-999 II Neo Zeong, Caça ao Fênix (Phoenix Hunt), Psycho-Frame.
        """;

    private static final String PROMPT = ContextoPrompt.montar("Mobile Suit Gundam NT (Narrative)", LORE);

    @Override public String getId() { return "gundam_nt"; }
    @Override public String getNomeExibicao() { return "Mobile Suit Gundam NT (Narrative)"; }
    @Override public String obterPromptSistema() { return PROMPT; }
}
