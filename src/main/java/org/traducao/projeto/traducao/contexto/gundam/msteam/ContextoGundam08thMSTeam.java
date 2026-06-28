package org.traducao.projeto.traducao.contexto.gundam.msteam;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoGundam08thMSTeam implements ProvedorContexto {

    private static final String LORE = """
        - Obra: Mobile Suit Gundam: The 08th MS Team, Universal Century 0079, Guerra de Um Ano no front terrestre do Sudeste Asiatico.
        - Faccao/forcas: Federacao Terrestre, Principado de Zeon, Kojima Battalion, 08th MS Team.
        - Principais nomes: Shiro Amada, Aina Sahalin, Karen Joshua, Terry Sanders Jr., Eledore Massis, Michel Ninorich, Kiki Rosita, Ginias Sahalin, Norris Packard.
        - Mobile suits e armas: RX-79[G] Ground Gundam, Gundam Ez8, Gouf Custom, Zaku, Magella Attack, Apsalus, Ball K-Type.
        - Termos UC: mobile suit, beam rifle, beam saber, colony, Zeon, Federation, Minovsky particles. Mantenha mobile suit em ingles.
        - Tom: guerra de selva, romance entre inimigos, soldados comuns e dilemas de campo; Shiro fala idealista e direto, Aina e contida, Norris tem honra militar solene.
        - Evite transformar dialogo militar em linguagem moderna demais; preserve patentes, ordens e radio-comunicacao com concisao.
        """;

    private static final String PROMPT = ContextoPrompt.montar("Mobile Suit Gundam: The 08th MS Team", LORE);

    @Override
    public String getId() { return "gundam_08ms"; }
    @Override
    public String getNomeExibicao() { return "Mobile Suit Gundam: The 08th MS Team"; }
    @Override
    public String obterPromptSistema() { return PROMPT; }
}
