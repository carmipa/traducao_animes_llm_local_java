package org.traducao.projeto.curatags;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SanitizadorTagsService {

    // LLM costuma alucinar chaves {texto} como marcação de pensamento, o que quebra a linha no Aegisub.
    private static final Pattern TAG_INVALIDA_PATTERN = Pattern.compile("\\{([^\\\\=}][^}]*)\\}");

    public String curarTags(String originalEn, String traduzidoPt) {
        if (originalEn == null || traduzidoPt == null) {
            return traduzidoPt;
        }

        String resultado = traduzidoPt;

        // Legado: LLM (ou versões antigas deste código) corrompiam a tag do Kara Templater
        // "{=X}" para "\N=X".
        resultado = resultado.replaceAll("\\\\N=(\\d+)\\{", "{=$1}{");
        resultado = resultado.replaceAll("\\\\N=(\\d+)", "{=$1}");

        // Formatação de tela (pos, cor, an8 etc.) sempre fica no prefixo {...} do início da linha.
        // Forçamos a tradução a ter exatamente o mesmo prefixo do original — inclusive quando o
        // original não tem prefixo nenhum, caso em que qualquer {...} que apareça na tradução é
        // alucinação do LLM e precisa ser descartado, não preservado.
        String prefixoOriginal = extrairPrefixo(originalEn);
        String textoTraduzidoSemPrefixo = removerPrefixo(resultado);
        resultado = prefixoOriginal + textoTraduzidoSemPrefixo;

        // Chaves remanescentes que não são tags válidas do ASS são alucinação do LLM;
        // escapamos para quebra de linha em vez de apagar o texto.
        Matcher matcher = TAG_INVALIDA_PATTERN.matcher(resultado);
        if (matcher.find()) {
            resultado = matcher.replaceAll("\\\\N$1");
        }

        resultado = resultado.replaceAll("\\}\\s+", "}");

        return resultado;
    }

    private String extrairPrefixo(String texto) {
        StringBuilder prefixo = new StringBuilder();
        int pos = 0;

        while (pos < texto.length()) {
            if (Character.isWhitespace(texto.charAt(pos))) {
                pos++;
                continue;
            }

            if (texto.charAt(pos) == '{') {
                int fechamento = texto.indexOf('}', pos);
                if (fechamento != -1) {
                    prefixo.append(texto.substring(pos, fechamento + 1));
                    pos = fechamento + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return prefixo.toString();
    }

    private String removerPrefixo(String texto) {
        int pos = 0;
        while (pos < texto.length()) {
            if (Character.isWhitespace(texto.charAt(pos))) {
                pos++;
                continue;
            }
            
            if (texto.charAt(pos) == '{') {
                int fechamento = texto.indexOf('}', pos);
                if (fechamento != -1) {
                    pos = fechamento + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return texto.substring(pos).trim();
    }
}
