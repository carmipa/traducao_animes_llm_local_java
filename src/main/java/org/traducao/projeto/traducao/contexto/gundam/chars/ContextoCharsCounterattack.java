package org.traducao.projeto.traducao.contexto.gundam.chars;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoCharsCounterattack implements ProvedorContexto {

    private static final String LORE = """
        - Obra: Mobile Suit Gundam: Char's Counterattack, Universal Century 0093.
        - Faccao/forcas: Londo Bell, Neo Zeon, Federacao Terrestre, Anaheim Electronics.
        - Principais nomes: Amuro Ray, Char Aznable, Bright Noa, Chan Agi, Beltorchika Irma, Hathaway Noa, Quess Paraya, Gyunei Guss, Nanai Miguel, Adenaur Paraya.
        - Lugares/eventos: Axis, Sweetwater, Luna II, Fifth Luna, queda de asteroide/colony drop-like operation.
        - Mobile suits: Nu Gundam, Sazabi, Re-GZ, Jegan, Geara Doga, Jagd Doga, Alpha Azieru.
        - Termos UC: Newtype, psycho-frame, psycommu, funnel, mobile suit, mobile armor, beam rifle, beam saber. Mantenha Newtype, psycho-frame e funnel.
        - Tom: confronto ideologico final entre Amuro e Char, melancolia politica e tensao catastrofica; Char fala carismatico e frio, Amuro direto e cansado.
        """;

    private static final String PROMPT = ContextoPrompt.montar("Mobile Suit Gundam: Char's Counterattack", LORE);

    @Override
    public String getId() { return "gundam_cca"; }
    @Override
    public String getNomeExibicao() { return "Gundam: Char's Counterattack"; }
    @Override
    public String obterPromptSistema() { return PROMPT; }
}
