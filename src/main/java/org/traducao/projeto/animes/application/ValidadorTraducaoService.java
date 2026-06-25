package org.traducao.projeto.animes.application;

import org.springframework.stereotype.Service;
import org.traducao.projeto.animes.domain.exceptions.AlucinacaoDetectadaException;

import java.util.regex.Pattern;

@Service
public class ValidadorTraducaoService {
    
    // Regras robustas importadas do pipeline Python, ampliadas após observar em
    // produção o Mistral Nemo deixar fragmentos como "exactly the same" sem
    // traduzir mesmo traduzindo o resto da fala corretamente.
    //
    // UNICODE_CHARACTER_CLASS e necessario aqui: sem ela, \b no Java so reconhece
    // [a-zA-Z0-9_] como caractere de palavra, entao letras acentuadas (ç, ã, é...)
    // contam como "fronteira", e palavras em portugues como "força" ou "esforço"
    // batem com "\bfor\b" e disparam falso positivo de "resíduo em inglês".
    private static final Pattern PADRAO_RESIDUO = Pattern.compile(
        "\\b(you|they|without|very|where|what|when|why|who|this|that|these|those|"
            + "and|the|is|are|was|were|have|has|with|from|exactly|same|not|but|for)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    // Evita os falsos positivos de "Abaixo a tirania" bloqueando apenas preâmbulos óbvios
    private static final Pattern PADRAO_PREAMBULO = Pattern.compile(
        "^(esta [ée] a tradu|abaixo seguem|aqui está a|tradução solicitada|a tradução seria)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    public void validarFala(String textoTraduzido) {
        if (textoTraduzido == null || textoTraduzido.trim().isEmpty()) {
            return;
        }
        
        if (PADRAO_RESIDUO.matcher(textoTraduzido).find()) {
            throw new AlucinacaoDetectadaException("Resíduo gringo detectado: " + textoTraduzido);
        }

        if (PADRAO_PREAMBULO.matcher(textoTraduzido).find()) {
            throw new AlucinacaoDetectadaException("Preâmbulo detectado: " + textoTraduzido);
        }
    }
}
