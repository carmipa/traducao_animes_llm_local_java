package org.traducao.projeto.raspagemRevisao.application;

import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemRevisao.domain.ResultadoDeteccaoConcordancia;

import java.util.ArrayList;
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
        "novo|velho|grande|pequeno|meu|seu|nosso|dele|pronto|cansado|sozinho|animado|nervoso|"
            + "preocupado|furioso|surpreso|certo|errado|bom|mau|feliz|triste|satisfeito|"
            + "irritado|confuso|ansioso|forte|fraco|jovem|lindo|feio|gentil|bravo|loco|louco";

    private static final String ADJ_FEM =
        "nova|velha|grande|pequena|minha|sua|nossa|dela|pronta|cansada|sozinha|animada|nervosa|"
            + "preocupada|furiosa|surpresa|certa|errada|boa|má|ma|feliz|triste|satisfeita|"
            + "irritada|confusa|ansiosa|forte|fraca|jovem|linda|feia|gentil|brava|loca|louca";

    private static final String PARTIC_MASC =
        "cansado|pronto|preocupado|animado|nervoso|sozinho|furioso|surpreso|certo|errado|"
            + "feliz|triste|satisfeito|irritado|confuso|ansioso|loco|louco";

    private static final String PARTIC_FEM =
        "cansada|pronta|preocupada|animada|nervosa|sozinha|furiosa|surpresa|certa|errada|"
            + "feliz|triste|satisfeita|irritada|confusa|ansiosa|loca|louca";

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

    private static final Pattern PRONOME_ARTIGO_ERRADO =
        Pattern.compile("\\b(o|um|do|no|ao|pelo|lo|no)\\s+ela\\b|\\b(a|uma|da|na|à|pela|la|na)\\s+ele\\b", FLAGS);

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

    private static final Pattern OBJETO_MASC_COM_HER_EN =
        Pattern.compile("\\b(para|com|de|a|ao|à|pela|pelo)\\s+(ele|o|lo|no|nele|dele|seu)\\b", FLAGS);

    private static final Pattern OBJETO_FEM_COM_HIM_EN =
        Pattern.compile("\\b(para|com|de|a|ao|à|pela|pelo)\\s+(ela|a|la|na|nela|dela)\\b", FLAGS);

    private static final Pattern IMPERATIVO_PARA_ELE_COM_HER =
        Pattern.compile("\\b(" + VERBO_IMPERATIVO + ")\\s+(a|para)\\s+ele\\b", FLAGS);

    private static final Pattern IMPERATIVO_PARA_ELA_COM_HIM =
        Pattern.compile("\\b(" + VERBO_IMPERATIVO + ")\\s+(a|para)\\s+ela\\b", FLAGS);

    private static final Pattern VI_ELE_COM_HER =
        Pattern.compile("\\b(vi|vejo|viu|vou ver|viemos ver|viram|amo|amei|odia|odeio|encontrei|encontrou|"
            + "conheci|conhece|ajudei|ajudou|protegi|protegeu)\\s+(ele|o|lo)\\b", FLAGS);

    private static final Pattern VI_ELA_COM_HIM =
        Pattern.compile("\\b(vi|vejo|viu|vou ver|viemos ver|viram|amo|amei|odia|odeio|encontrei|encontrou|"
            + "conheci|conhece|ajudei|ajudou|protegi|protegeu)\\s+(ela|a|la)\\b", FLAGS);

    private static final Pattern SUJEITO_ELE_COM_SHE =
        Pattern.compile("(?:^|[.!?…]\\s*|,\\s*)ele\\s+(disse|falou|gritou|sussurrou|pensou|riu|chorou|"
            + "sorriu|perguntou|respondeu|replicou|murmurou|exclamou|continuou|começou|comecou|parou|foi|"
            + "está|esta|estava|é|era|será|sera|ficou|parece|sabe|sabia|quer|queria|pode|podia|vai|ia)\\b", FLAGS);

    private static final Pattern SUJEITO_ELA_COM_HE =
        Pattern.compile("(?:^|[.!?…]\\s*|,\\s*)ela\\s+(disse|falou|gritou|sussurrou|pensou|riu|chorou|"
            + "sorriu|perguntou|respondeu|replicou|murmurou|exclamou|continuou|começou|comecou|parou|foi|"
            + "está|esta|estava|é|era|será|sera|ficou|parece|sabe|sabia|quer|queria|pode|podia|vai|ia)\\b", FLAGS);

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

        if (originalIngles != null && !originalIngles.isBlank()) {
            String original = removerTagsAss(originalIngles);
            detectarPronomesECruzamento(original, texto, motivos);
            detectarTratamentos(original, texto, motivos);
            detectarVerboPredicado(original, texto, motivos);
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
            if (PARTIC_MASC_APOS_VERBO.matcher(texto).find()
                && !HE_EN.matcher(original).find()) {
                motivos.add("Original indica personagem/falante feminino ('she'), mas predicado está no masculino");
            }
        }

        if (HE_EN.matcher(original).find()) {
            adicionarSeEncontrado(motivos, SUJEITO_ELA_COM_HE, texto,
                "Original usa 'he', mas sujeito da tradução é 'ela'");
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

    private void detectarVerboPredicado(String original, String texto, Set<String> motivos) {
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
