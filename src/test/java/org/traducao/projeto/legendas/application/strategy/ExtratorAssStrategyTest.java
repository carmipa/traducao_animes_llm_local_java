package org.traducao.projeto.legendas.application.strategy;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.legendas.domain.FaixaLegenda;
import org.traducao.projeto.legendas.domain.FormatoLegenda;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExtratorAssStrategyTest {

    private final ExtratorAssStrategy strategy = new ExtratorAssStrategy();

    @Test
    void suportaApenasFormatoAss() {
        assertThat(strategy.suporta(FormatoLegenda.ASS)).isTrue();
        assertThat(strategy.suporta(FormatoLegenda.PGS)).isFalse();
        assertThat(strategy.suporta(FormatoLegenda.SRT)).isFalse();
    }

    @Test
    void retornaVazioQuandoNaoHaFaixaAss() {
        List<FaixaLegenda> faixas = List.of(
            new FaixaLegenda(0, "subtitles", "HDMV PGS", "S_HDMV/PGS", "eng", "Signs", false, false)
        );

        assertThat(strategy.selecionarMelhorFaixa(faixas)).isEmpty();
    }

    @Test
    void preferePorPalavraChaveNoNomeDaFaixa() {
        FaixaLegenda signs = new FaixaLegenda(0, "subtitles", "SubStation Alpha", "S_TEXT/ASS", "eng", "Signs", false, false);
        FaixaLegenda dialogue = new FaixaLegenda(1, "subtitles", "SubStation Alpha", "S_TEXT/ASS", "eng", "Dialogue Full", false, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(signs, dialogue));

        assertThat(resultado).contains(dialogue);
    }

    @Test
    void caiParaUltimaCandidataQuandoNenhumaPalavraChaveBate() {
        FaixaLegenda primeira = new FaixaLegenda(0, "subtitles", "ass", "S_TEXT/ASS", "eng", "Track A", false, false);
        FaixaLegenda segunda = new FaixaLegenda(1, "subtitles", "ass", "S_TEXT/ASS", "eng", "Track B", false, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(primeira, segunda));

        assertThat(resultado).contains(segunda);
    }
}
