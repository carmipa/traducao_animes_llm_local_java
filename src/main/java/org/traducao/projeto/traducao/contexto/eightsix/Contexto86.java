package org.traducao.projeto.traducao.contexto.eightsix;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.contexto.ContextoPrompt;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

@Component
public class Contexto86 implements ProvedorContexto {

    private static final String LORE = """
        - Obra: 86 - Eighty-Six.
        - A Republica de San Magnolia afirma lutar com drones nao tripulados, mas envia os Eighty-Six, pessoas perseguidas como Colorata, para pilotar Juggernauts nos campos de batalha.
        - Mantenha "Eighty-Six" ou "86" como designacao social/militar; nao traduza como "oitenta e seis" salvo fala explicitamente numerica.
        - Faccao/termos: Republica de San Magnolia, Imperio de Giad, Federacao de Giad, Legion, Alba, Colorata, Para-RAID, Handler, Processor, Juggernaut, Feldress, Reginleif, Morpho.
        - Unidades: Spearhead Squadron, Nordlicht Squadron; use Esquadrao Spearhead e Esquadrao Nordlicht.
        - Principais nomes: Shinei "Shin" Nouzen, Vladilena "Lena" Milize, Raiden Shuga, Anju Emma, Theoto Rikka, Kurena Kukumila, Frederica Rosenfort, Ernst Zimmerman, Eugene Rantz.
        - Codinomes importantes: Undertaker para Shin; Bloodstained Queen para Lena quando aparecer.
        - Temas: guerra, desumanizacao, racismo institucional, trauma, sobrevivencia e dignidade. Evite suavizar termos duros quando a cena denuncia opressao.
        - Tom: militar e emocionalmente contido; Shin e seco, Lena e formal/idealista, os membros do Spearhead usam ironia amarga e intimidade de esquadrao.
        """;

    private static final String PROMPT = ContextoPrompt.montar("86 - Eighty-Six", LORE);

    @Override
    public String getId() { return "eight_six"; }
    @Override
    public String getNomeExibicao() { return "86 (Eighty-Six)"; }
    @Override
    public String obterPromptSistema() { return PROMPT; }
}
