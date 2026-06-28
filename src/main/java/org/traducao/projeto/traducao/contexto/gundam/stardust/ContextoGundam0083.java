package org.traducao.projeto.traducao.contexto.gundam.stardust;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class ContextoGundam0083 implements ProvedorContexto {

    private static final String LORE = """
        - Obra: Mobile Suit Gundam 0083: Stardust Memory, Universal Century 0083, ponte entre a Guerra de Um Ano e Zeta Gundam.
        - Faccao/eventos: Federacao Terrestre, remanescentes de Zeon, Delaz Fleet, Operation Stardust, surgimento politico dos Titans.
        - Principais nomes: Kou Uraki, Anavel Gato, Nina Purpleton, South Burning, Cima Garahau, Aiguille Delaz, Chuck Keith, Mora Bascht.
        - Naves/base: Albion, Anaheim Electronics, Naval Review, Solomon/Confeito.
        - Mobile suits: Gundam GP01 Zephyranthes, GP01Fb Full Burnern, GP02A Physalis, GP03 Dendrobium/Stamen, Gerbera Tetra, Neue Ziel, GM Custom.
        - Termos UC: mobile suit, mobile armor, colony drop, beam rifle, beam saber, Minovsky particles, Newtype. Mantenha mobile suit/mobile armor.
        - Tom: drama militar de conspiracao, honra de Zeon, trauma pos-guerra e escalada politica; Gato fala com solenidade idealista, Kou com impulsividade de piloto jovem.
        """;

    private static final String PROMPT = ContextoPrompt.montar("Mobile Suit Gundam 0083: Stardust Memory", LORE);

    @Override
    public String getId() { return "gundam_0083"; }
    @Override
    public String getNomeExibicao() { return "Mobile Suit Gundam 0083: Stardust Memory"; }
    @Override
    public String obterPromptSistema() { return PROMPT; }
}
