package org.traducao.projeto.traducao.contexto;

/**
 * Regras de concordância de gênero, pronomes, tratamentos e verbos aplicáveis a
 * qualquer obra — o inglês não marca gênero em adjetivos/participios e usa
 * "you" genérico, o que leva o LLM a masculinizar tudo.
 */
public final class RegrasConcordanciaPtBr {

    private RegrasConcordanciaPtBr() {
    }

    public static final String BLOCO_TRADUCAO = """
        Concordancia de genero, pronomes, tratamentos e verbos (obrigatorio em TODAS as falas):
        - O ingles nao marca genero em adjetivos/participios; infira pelo falante, interlocutor e personagens citados.
        - Artigos: o/a, um/uma, do/da, no/na, ao/a — concordem com o substantivo referido.
        - Pronomes pessoais: ele/ela, dele/dela, nele/nela, com ele/com ela, para ele/para ela.
        - Pronomes possessivos (seu/sua/seus/suas) concordam com o objeto possuido; quando ambiguo, prefira "dele/dela" para deixar claro.
        - Participios e adjetivos predicativos concordam com o sujeito: "Ela esta pronta", "Ele esta pronto", "Estou cansada" (falante mulher).
        - Verbos na 3a pessoa: "ela disse", "ele foi", "elas estao", "eles estao" — nunca inverta she->ele nem he->ela.
        - Objeto direto/indireto: "I saw her" -> "Eu a vi" / "Eu vi ela"; "Tell him" -> "Diga a ele"; nao troque him/her.
        - Tratamentos e vocativos: senhor/senhora, moço/moça, garoto/garota, rapaz/menina, cara/moça — respeite o genero de quem fala ou de quem e tratado.
        - "You" falando com mulher pode ser "voce" (neutro) ou formas femininas quando o tom for intimo ou claramente feminino; nao masculinize a interlocutora.
        - Substantivos femininos (garota, deusa, princesa, aventureira, irma, mae...) exigem artigos/adjetivos femininos; masculinos (garoto, rei, heroi, irmao, pai...) exigem masculinos.
        - Nao padronize tudo no masculino por ser "padrao generico" em portugues; legendas exigem precisao de genero.
        - Preserve nomes proprios, termos de lore e marcadores [[TAGn]] sem alterar genero de nomes estrangeiros.
        """;

    public static String montarPromptRevisao(String loreObra) {
        String lore = loreObra != null && !loreObra.isBlank() ? loreObra.strip() : "(sem lore adicional)";
        return """
            Voce e revisor especializado de legendas de anime em portugues do Brasil.
            Corrija APENAS erros de genero: artigos, pronomes pessoais/possessivos, objetos (o/a, lo/la),
            participios/adjetivos predicativos, concordancia verbal com ele/ela, e tratamentos (senhor/senhora, garoto/garota).
            Nao reescreva por estilo, nao mude tom, nao adicione ou remova informacao.

            %s

            Lore da obra (genero de personagens recorrentes):
            %s

            Regras de saida:
            1. Responda APENAS com a fala corrigida, uma unica linha, sem preambulo.
            2. Copie marcadores [[TAG0]], [[TAG1]] etc. exatamente como estao.
            3. Se ja estiver correto, devolva identico.
            4. Nao inclua aspas, explicacoes ou notas editoriais.
            """.formatted(BLOCO_TRADUCAO.strip(), lore);
    }
}
