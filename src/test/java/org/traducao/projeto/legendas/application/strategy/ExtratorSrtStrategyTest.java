package org.traducao.projeto.legendas.application.strategy;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.legendas.domain.FaixaLegenda;
import org.traducao.projeto.legendas.domain.FormatoLegenda;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExtratorSrtStrategyTest {

    private final ExtratorSrtStrategy strategy = new ExtratorSrtStrategy();

    @Test
    void suportaApenasFormatoSrt() {
        assertThat(strategy.suporta(FormatoLegenda.SRT)).isTrue();
        assertThat(strategy.suporta(FormatoLegenda.ASS)).isFalse();
        assertThat(strategy.suporta(FormatoLegenda.PGS)).isFalse();
    }

    @Test
    void retornaVazioQuandoNaoHaFaixaSrt() {
        List<FaixaLegenda> faixas = List.of(
            new FaixaLegenda(0, "subtitles", "HDMV PGS", "S_HDMV/PGS", "eng", "Signs", false, false)
        );

        assertThat(strategy.selecionarMelhorFaixa(faixas)).isEmpty();
    }

    @Test
    void preferePistaPadraoOuComIdiomaEsperado() {
        FaixaLegenda outraIdioma = new FaixaLegenda(0, "subtitles", "SubRip/SRT", "S_TEXT/UTF8", "spa", "Spanish", false, false);
        FaixaLegenda padrao = new FaixaLegenda(1, "subtitles", "SubRip/SRT", "S_TEXT/UTF8", "eng", "English", true, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(outraIdioma, padrao));

        assertThat(resultado).contains(padrao);
    }

    @Test
    void caiParaPrimeiraCandidataQuandoNenhumaEhPadraoOuEsperada() {
        FaixaLegenda primeira = new FaixaLegenda(0, "subtitles", "SubRip/SRT", "S_TEXT/UTF8", "spa", "Spanish", false, false);
        FaixaLegenda segunda = new FaixaLegenda(1, "subtitles", "SubRip/SRT", "S_TEXT/UTF8", "fra", "French", false, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(primeira, segunda));

        assertThat(resultado).contains(primeira);
    }
}
