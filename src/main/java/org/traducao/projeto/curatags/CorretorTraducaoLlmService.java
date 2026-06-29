package org.traducao.projeto.curatags;

import org.springframework.stereotype.Service;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;

import java.util.Optional;

@Service
public class CorretorTraducaoLlmService {

    private final MistralPort mistralPort;
    private final MascaradorTags mascaradorTags;
    private final ValidadorTraducaoService validador;

    public CorretorTraducaoLlmService(MistralPort mistralPort, MascaradorTags mascaradorTags, ValidadorTraducaoService validador) {
        this.mistralPort = mistralPort;
        this.mascaradorTags = mascaradorTags;
        this.validador = validador;
    }

    /**
     * Retorna a tradução corrigida via LLM apenas se a tradução atual estiver com
     * resíduo em inglês/preâmbulo (ValidadorTraducaoService) — evita chamar o LLM
     * para falas que já estão corretas.
     */
    public Optional<String> corrigirSeNecessario(String originalEn, String traduzidoAtual) {
        String motivo;
        try {
            validador.validarFala(traduzidoAtual);
            return Optional.empty();
        } catch (AlucinacaoDetectadaException e) {
            motivo = e.getMessage();
        }

        MascaradorTags.Mascarado mascOriginal = mascaradorTags.mascarar(originalEn != null ? originalEn : "");
        MascaradorTags.Mascarado mascTraduzido = mascaradorTags.mascarar(traduzidoAtual);

        Optional<String> resposta = mistralPort.corrigirTraducao(
            mascOriginal.texto(),
            mascTraduzido.texto(),
            motivo
        );
        if (resposta.isEmpty()) {
            return Optional.empty();
        }

        try {
            String desmascarado = mascaradorTags.desmascarar(resposta.get(), mascTraduzido.tags());
            validador.validarFala(desmascarado);
            return Optional.of(desmascarado);
        } catch (AlucinacaoDetectadaException e) {
            return Optional.empty();
        }
    }
}
