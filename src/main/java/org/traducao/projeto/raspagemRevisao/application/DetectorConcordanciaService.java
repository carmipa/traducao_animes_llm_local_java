package org.traducao.projeto.raspagemRevisao.application;

import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemRevisao.domain.ResultadoDeteccaoConcordancia;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heurísticas para calques de gênero do inglês: concordância nominal,
 * pronomes pessoais/objetos, tratamentos e predicados verbais.
 */
@Service
public class DetectorConcordanciaService {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final String SUBST_FEM =
        "menina|garota|moça|moca|mulher|deusa|princesa|heroina|heroína|rainha|senhora|"
            + "irmã|irma|mãe|mae|filha|avó|tia|amiga|dama|donzela|aventureira|sacerdotisa|"
            + "feiticeira|amazona|ladra|ladrona|deusa|moça|moca";

    private static final String SUBST_MASC =
        "menino|garoto|moço|moco|homem|deus|príncipe|principe|irmão|irmao|pai|filho|avô|"
            + "tio|amigo|rei|herói|heroi|aventureiro|novato|campeão|campeao|rapaz|cara|"
            + "sacerdote|mago|ladrao|ladrão|deus|garoto";

    private static final String ADJ_MASC =
        "novo|velho|pequeno|meu|seu|nosso|pronto|cansado|sozinho|animado|nervoso|"
            + "preocupado|furioso|surpreso|certo|errado|bom|mau|satisfeito|"
            + "irritado|confuso|ansioso|fraco|lindo|feio|bravo|loco|louco|"
            + "assustado|machucado|ferido|ocupado|perdido|vivo|morto|bêbado|bebado|doido";

    private static final String ADJ_FEM =
        "nova|velha|pequena|minha|sua|nossa|pronta|cansada|sozinha|animada|nervosa|"
            + "preocupada|furiosa|surpresa|certa|errada|boa|má|ma|satisfeita|"
            + "irritada|confusa|ansiosa|fraca|linda|feia|brava|loca|louca|"
            + "assustada|machucada|ferida|ocupada|perdida|viva|morta|bêbada|bebada|doida";

    private static final String PARTIC_MASC =
        "cansado|pronto|preocupado|animado|nervoso|sozinho|furioso|surpreso|certo|errado|"
            + "satisfeito|irritado|confuso|ansioso|loco|louco|assustado|machucado|ferido|"
            + "ocupado|perdido|vivo|morto|bêbado|bebado|doido";

    private static final String PARTIC_FEM =
        "cansada|pronta|preocupada|animada|nervosa|sozinha|furiosa|surpresa|certa|errada|"
            + "satisfeita|irritada|confusa|ansiosa|loca|louca|assustada|machucada|ferida|"
            + "ocupada|perdida|viva|morta|bêbada|bebada|doida";

    private static final String TRATAMENTO_MASC = "senhor|moço|moco|garoto|rapaz|cara|homem|menino|irmão|irmao|pai";
    private static final String TRATAMENTO_FEM = "senhora|moça|moca|garota|menina|dama|irmã|irma|mãe|mae|donzela";

    private static final String VERBO_AUX =
        "está|esta|estava|é|era|foi|será|sera|ficou|parece|continua|ficará|ficara|estará|estara|"
            + "estão|estao|foram|eram|serão|serao|ficaram|parecem|continuam";

    private static final String VERBO_IMPERATIVO =
        "diga|fale|fala|pergunte|pergunte|avise|mande|manda|chame|chama|espere|espera|"
            + "olhe|olha|escute|escuta|veja|ve|ouça|ouca|deixe|deixa";

    private static final Pattern ART_MASC_COM_SUBST_FEM =
        Pattern.compile("\\b(o|um|este|esse|aquele|do|no|ao|pelo|num)\\s+(" + SUBST_FEM + ")\\b", FLAGS);

    private static final Pattern ART_FEM_COM_SUBST_MASC =
        Pattern.compile("\\b(a|uma|esta|essa|aquela|da|na|à|pela|numa)\\s+(" + SUBST_MASC + ")\\b", FLAGS);

    private static final Pattern ADJ_MASC_COM_SUBST_FEM =
        Pattern.compile("\\b(" + ADJ_MASC + ")\\s+(" + SUBST_FEM + ")\\b", FLAGS);

    private static final Pattern ADJ_FEM_COM_SUBST_MASC =
        Pattern.compile("\\b(" + ADJ_FEM + ")\\s+(" + SUBST_MASC + ")\\b", FLAGS);

    private static final Pattern SUBST_FEM_COM_ADJ_MASC =
        Pattern.compile("\\b(" + SUBST_FEM + ")\\s+(" + ADJ_MASC + ")\\b", FLAGS);

    private static final Pattern SUBST_MASC_COM_ADJ_FEM =
        Pattern.compile("\\b(" + SUBST_MASC + ")\\s+(" + ADJ_FEM + ")\\b", FLAGS);

    // "a" sozinho fica fora do segundo grupo: é a preposição invariante em gênero
    // ("disse a ele" / "disse a ela" são ambos corretos), não o artigo feminino —
    // incluí-lo fazia "a ele" (construção comum e correta) ser sinalizado sempre.
    private static final Pattern PRONOME_ARTIGO_ERRADO =
        Pattern.compile("\\b(o|um|do|no|ao|pelo|lo|no)\\s+ela\\b|\\b(uma|da|na|à|pela|la)\\s+ele\\b", FLAGS);

    private static final Pattern PRONOME_FEMININO_EN = Pattern.compile(
        "\\b(she|her|hers|girl|woman|lady|mother|mom|sister|daughter|"
            + "princess|goddess|queen|heroine|miss|mrs|ms|madam|ma'am|female|wife|aunt|"
            + "grandma|grandmother|niece|waitress|actress|hostess)\\b", FLAGS);

    private static final Pattern PRONOME_MASCULINO_EN = Pattern.compile(
        "\\b(he|him|his|boy|man|guy|father|dad|brother|son|prince|god|king|"
            + "hero|mr|sir|male|husband|uncle|grandpa|grandfather|nephew|waiter|actor)\\b", FLAGS);

    private static final Pattern HER_EN = Pattern.compile("\\bher\\b", FLAGS);
    private static final Pattern HIM_EN = Pattern.compile("\\bhim\\b", FLAGS);
    private static final Pattern SHE_EN = Pattern.compile("\\bshe\\b", FLAGS);
    private static final Pattern HE_EN = Pattern.compile("\\bhe\\b", FLAGS);

    private static final Pattern PARTIC_MASC_APOS_VERBO =
        Pattern.compile("\\b(" + VERBO_AUX + "|se sente|me sinto|sinto-me|sinto me)\\s+(" + PARTIC_MASC + ")\\b", FLAGS);

    private static final Pattern PARTIC_FEM_APOS_VERBO =
        Pattern.compile("\\b(" + VERBO_AUX + "|se sente|me sinto|sinto-me|sinto me)\\s+(" + PARTIC_FEM + ")\\b", FLAGS);

    private static final Pattern ELA_COM_PREDICADO_MASC =
        Pattern.compile("\\bela\\s+(" + VERBO_AUX + ")\\s+(" + PARTIC_MASC + ")\\b", FLAGS);

    private static final Pattern ELE_COM_PREDICADO_FEM =
        Pattern.compile("\\bele\\s+(" + VERBO_AUX + ")\\s+(" + PARTIC_FEM + ")\\b", FLAGS);

    private static final Pattern ELAS_COM_PREDICADO_MASC =
        Pattern.compile("\\belas\\s+(" + VERBO_AUX + ")\\s+(" + PARTIC_MASC + ")\\b", FLAGS);

    private static final Pattern ELES_COM_PREDICADO_FEM =
        Pattern.compile("\\beles\\s+(" + VERBO_AUX + ")\\s+(" + PARTIC_FEM + ")\\b", FLAGS);

    private static final Pattern INGLES_FALA_AMBIGUA =
        Pattern.compile("\\b(i|i'm|im|i am|me|my|you|you're|you are|your)\\b", FLAGS);

    private static final Pattern MASCULINO_AMBIGUO_PT =
        Pattern.compile("\\b("
            + "estou|to|tô|estava|fiquei|fico|vou ficar|me sinto|sinto-me|sinto me|"
            + "voce esta|você está|voce ta|você tá|tu estas|tu estás|tu ta|tu tá|"
            + "esta|está|estas|estás|ta|tá"
            + ")\\s+(" + PARTIC_MASC + ")\\b|\\bobrigado\\b", FLAGS);

    private static final String PREPOSICOES_OBJETO = "para|com|de|a|ao|à|pela|pelo";

    private static final String VERBOS_TRANSITIVOS_DIRETOS =
        "vi|vejo|viu|vou ver|viemos ver|viram|amo|amei|odia|odeio|encontrei|encontrou|"
            + "conheci|conhece|ajudei|ajudou|protegi|protegeu";

    private static final Pattern OBJETO_MASC_COM_HER_EN =
        Pattern.compile("\\b(" + PREPOSICOES_OBJETO + ")\\s+(ele|nele|dele)\\b", FLAGS);

    private static final Pattern OBJETO_FEM_COM_HIM_EN =
        Pattern.compile("\\b(" + PREPOSICOES_OBJETO + ")\\s+(ela|nela|dela)\\b", FLAGS);

    private static final Pattern IMPERATIVO_PARA_ELE_COM_HER =
        Pattern.compile("\\b(" + VERBO_IMPERATIVO + ")\\s+(a|para)\\s+ele\\b", FLAGS);

    private static final Pattern IMPERATIVO_PARA_ELA_COM_HIM =
        Pattern.compile("\\b(" + VERBO_IMPERATIVO + ")\\s+(a|para)\\s+ela\\b", FLAGS);

    private static final Pattern VI_ELE_COM_HER =
        Pattern.compile("\\b(" + VERBOS_TRANSITIVOS_DIRETOS + ")\\s+(ele|o|lo)\\b", FLAGS);

    private static final Pattern VI_ELA_COM_HIM =
        Pattern.compile("\\b(" + VERBOS_TRANSITIVOS_DIRETOS + ")\\s+(ela|a|la)\\b", FLAGS);

    private static final String VERBOS_SUJEITO =
        "disse|diz|dizia|falou|fala|falava|gritou|grita|gritava|sussurrou|sussurra|pensou|pensa|pensava|"
            + "riu|ri|chorou|chora|sorriu|sorri|perguntou|pergunta|perguntava|respondeu|responde|respondia|"
            + "replicou|replica|murmurou|murmura|exclamou|exclama|continuou|continua|começou|comecou|começa|comeca|"
            // "para" sozinho fica de fora: é, de longe, mais comum como preposição/marcador
            // de oração final ("ele para esperar" = "for him to wait") do que como o verbo
            // "parar" — incluí-lo causava falso positivo em toda frase com essa construção.
            + "parou|para de|foi|vai|ia|está|esta|estava|é|era|será|sera|ficou|fica|parece|parecia|sabe|sabia|"
            + "quer|queria|pode|podia|mencionou|menciona|afirmou|afirma|contou|conta|explicou|explica|"
            + "prometeu|promete|chamou|chama|viu|vê|ve|ouviu|ouve|escutou|escuta|achou|acha|sentiu|sente|"
            + "olhou|olha|concordou|concorda|trabalhou|trabalha|morou|mora|viveu|vive|fez|faz|faria";

    private static final Pattern SUJEITO_ELE_COM_SHE =
        Pattern.compile("\\bele\\s+(" + VERBOS_SUJEITO + ")\\b", FLAGS);

    private static final Pattern SUJEITO_ELA_COM_HE =
        Pattern.compile("\\bela\\s+(" + VERBOS_SUJEITO + ")\\b", FLAGS);

    // "ele"/"ela" como objeto direto/oblíquo (vi ele, com ela, para ele...) é uso
    // pronominal correto em PT-BR mesmo quando o original só menciona o outro
    // gênero (ex.: "She told him" -> "Ela disse a ele"). Por isso esses usos são
    // removidos do texto antes de checar o isolado (ver removerObjetoPronominal).
    // Nota: um lookbehind negativo equivalente (?<!prep|verbo\s+) chega a dar
    // falso-negativo no JDK quando a alternância mistura frases com espaço
    // (ex.: "viemos ver") com palavras simples — por isso o "strip primeiro".
    private static final Pattern OBJETO_PRONOMINAL_ELE_ELA = Pattern.compile(
        "\\b(?:" + PREPOSICOES_OBJETO + "|" + VERBOS_TRANSITIVOS_DIRETOS + ")\\s+(?:ele|ela)\\b", FLAGS);
    private static final Pattern ELE_ISOLADO = Pattern.compile("\\bele\\b", FLAGS);
    private static final Pattern ELA_ISOLADA = Pattern.compile("\\bela\\b", FLAGS);

    private static String removerObjetoPronominal(String texto) {
        return OBJETO_PRONOMINAL_ELE_ELA.matcher(texto).replaceAll(" ");
    }

    private static final Pattern TRATAMENTO_MASC_COM_FEM_EN =
        Pattern.compile("\\b(" + TRATAMENTO_MASC + ")\\b", FLAGS);

    private static final Pattern TRATAMENTO_FEM_COM_MASC_EN =
        Pattern.compile("\\b(" + TRATAMENTO_FEM + ")\\b", FLAGS);

    private static final Pattern DELE_COM_HER =
        Pattern.compile("\\bdele\\b", FLAGS);

    private static final Pattern DELA_COM_HIM =
        Pattern.compile("\\bdela\\b", FLAGS);

    public ResultadoDeteccaoConcordancia analisar(String originalIngles, String traducaoPt) {
        if (traducaoPt == null || traducaoPt.isBlank()) {
            return ResultadoDeteccaoConcordancia.limpo();
        }

        String texto = removerTagsAss(traducaoPt);
        Set<String> motivos = new LinkedHashSet<>();

        detectarConcordanciaNominal(texto, motivos);
        detectarVerboPredicado(texto, motivos);

        if (originalIngles != null && !originalIngles.isBlank()) {
            String original = removerTagsAss(originalIngles);
            detectarPronomesECruzamento(original, texto, motivos);
            detectarTratamentos(original, texto, motivos);
        }

        if (motivos.isEmpty()) {
            return ResultadoDeteccaoConcordancia.limpo();
        }
        return new ResultadoDeteccaoConcordancia(true, List.copyOf(motivos));
    }

    private void detectarConcordanciaNominal(String texto, Set<String> motivos) {
        adicionarSeEncontrado(motivos, ART_MASC_COM_SUBST_FEM, texto,
            "Artigo/pronome demonstrativo masculino antes de substantivo feminino");
        adicionarSeEncontrado(motivos, ART_FEM_COM_SUBST_MASC, texto,
            "Artigo/pronome demonstrativo feminino antes de substantivo masculino");
        adicionarSeEncontrado(motivos, ADJ_MASC_COM_SUBST_FEM, texto,
            "Adjetivo masculino antes de substantivo feminino");
        adicionarSeEncontrado(motivos, ADJ_FEM_COM_SUBST_MASC, texto,
            "Adjetivo feminino antes de substantivo masculino");
        adicionarSeEncontrado(motivos, SUBST_FEM_COM_ADJ_MASC, texto,
            "Substantivo feminino com adjetivo/particípio masculino");
        adicionarSeEncontrado(motivos, SUBST_MASC_COM_ADJ_FEM, texto,
            "Substantivo masculino com adjetivo/particípio feminino");
        adicionarSeEncontrado(motivos, PRONOME_ARTIGO_ERRADO, texto,
            "Artigo/pronome oblíquo incompatível (o ela / a ele / lo ela)");
    }

    private void detectarPronomesECruzamento(String original, String texto, Set<String> motivos) {
        if (HER_EN.matcher(original).find()) {
            adicionarSeEncontrado(motivos, OBJETO_MASC_COM_HER_EN, texto,
                "Original usa 'her', mas tradução aponta para masculino (ele/o/dele/para ele)");
            adicionarSeEncontrado(motivos, IMPERATIVO_PARA_ELE_COM_HER, texto,
                "Original usa 'her', mas imperativo dirige-se a 'ele'");
            adicionarSeEncontrado(motivos, VI_ELE_COM_HER, texto,
                "Original usa 'her', mas verbo rege pronome/objeto masculino");
            if (DELE_COM_HER.matcher(texto).find() && !contemIndicioFemininoPt(texto)) {
                motivos.add("Original usa 'her', mas tradução usa 'dele' (possessivo masculino)");
            }
        }

        if (HIM_EN.matcher(original).find()) {
            adicionarSeEncontrado(motivos, OBJETO_FEM_COM_HIM_EN, texto,
                "Original usa 'him', mas tradução aponta para feminino (ela/a/dela/para ela)");
            adicionarSeEncontrado(motivos, IMPERATIVO_PARA_ELA_COM_HIM, texto,
                "Original usa 'him', mas imperativo dirige-se a 'ela'");
            adicionarSeEncontrado(motivos, VI_ELA_COM_HIM, texto,
                "Original usa 'him', mas verbo rege pronome/objeto feminino");
            if (DELA_COM_HIM.matcher(texto).find() && !contemIndicioFemininoPt(texto)) {
                motivos.add("Original usa 'him', mas tradução usa 'dela' (possessivo feminino)");
            }
        }

        if (SHE_EN.matcher(original).find()) {
            adicionarSeEncontrado(motivos, SUJEITO_ELE_COM_SHE, texto,
                "Original usa 'she', mas sujeito da tradução é 'ele'");
            if (!HE_EN.matcher(original).find() && ELE_ISOLADO.matcher(removerObjetoPronominal(texto)).find()) {
                motivos.add("Original usa 'she' sem referência masculina, mas a tradução contém o masculino 'ele'");
            }
            if (PARTIC_MASC_APOS_VERBO.matcher(texto).find()
                && !HE_EN.matcher(original).find()) {
                motivos.add("Original indica personagem/falante feminino ('she'), mas predicado está no masculino");
            }
        }

        if (HE_EN.matcher(original).find()) {
            adicionarSeEncontrado(motivos, SUJEITO_ELA_COM_HE, texto,
                "Original usa 'he', mas sujeito da tradução é 'ela'");
            if (!SHE_EN.matcher(original).find() && ELA_ISOLADA.matcher(removerObjetoPronominal(texto)).find()) {
                motivos.add("Original usa 'he' sem referência feminina, mas a tradução contém o feminino 'ela'");
            }
            if (PARTIC_FEM_APOS_VERBO.matcher(texto).find()
                && !SHE_EN.matcher(original).find()) {
                motivos.add("Original indica personagem/falante masculino ('he'), mas predicado está no feminino");
            }
        }

        if (PRONOME_FEMININO_EN.matcher(original).find()
            && PARTIC_MASC_APOS_VERBO.matcher(texto).find()
            && !PRONOME_MASCULINO_EN.matcher(original).find()) {
            motivos.add("Original indica feminino, mas participio/adjetivo predicativo está no masculino");
        }

        if (PRONOME_MASCULINO_EN.matcher(original).find()
            && PARTIC_FEM_APOS_VERBO.matcher(texto).find()
            && !PRONOME_FEMININO_EN.matcher(original).find()) {
            motivos.add("Original indica masculino, mas participio/adjetivo predicativo está no feminino");
        }

        if (!PRONOME_FEMININO_EN.matcher(original).find()
            && !PRONOME_MASCULINO_EN.matcher(original).find()
            && INGLES_FALA_AMBIGUA.matcher(original).find()
            && MASCULINO_AMBIGUO_PT.matcher(texto).find()) {
            motivos.add("Original não indica gênero, mas a tradução usa masculino marcado; revisar pela lore ou neutralizar");
        }
    }

    private void detectarTratamentos(String original, String texto, Set<String> motivos) {
        boolean femEn = PRONOME_FEMININO_EN.matcher(original).find();
        boolean mascEn = PRONOME_MASCULINO_EN.matcher(original).find();

        if (femEn && !mascEn) {
            adicionarSeEncontrado(motivos, TRATAMENTO_MASC_COM_FEM_EN, texto,
                "Tratamento/vocativo masculino (senhor/garoto/moço) com referência feminina no original");
        }
        if (mascEn && !femEn) {
            adicionarSeEncontrado(motivos, TRATAMENTO_FEM_COM_MASC_EN, texto,
                "Tratamento/vocativo feminino (senhora/garota/moça) com referência masculina no original");
        }
    }

    private void detectarVerboPredicado(String texto, Set<String> motivos) {
        adicionarSeEncontrado(motivos, ELA_COM_PREDICADO_MASC, texto,
            "Sujeito 'ela' com predicado/adjetivo no masculino");
        adicionarSeEncontrado(motivos, ELE_COM_PREDICADO_FEM, texto,
            "Sujeito 'ele' com predicado/adjetivo no feminino");
        adicionarSeEncontrado(motivos, ELAS_COM_PREDICADO_MASC, texto,
            "Sujeito 'elas' com predicado no masculino");
        adicionarSeEncontrado(motivos, ELES_COM_PREDICADO_FEM, texto,
            "Sujeito 'eles' com predicado no feminino");
    }

    private static boolean contemIndicioFemininoPt(String texto) {
        return Pattern.compile("\\b(" + SUBST_FEM + "|ela|elas|dela|delas|nela|a ela)\\b", FLAGS)
            .matcher(texto).find();
    }

    private static void adicionarSeEncontrado(
        Set<String> motivos, Pattern pattern, String texto, String descricao
    ) {
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            motivos.add(descricao + ": \"" + matcher.group().trim() + "\"");
        }
    }

    private static String removerTagsAss(String texto) {
        return texto.replaceAll("\\{[^{}]*}", " ")
            .replace("\\N", " ")
            .replace("\\n", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
