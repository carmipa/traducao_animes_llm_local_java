package org.traducao.projeto.legendasExtracao.application.strategy;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorPgsStrategy;
import org.traducao.projeto.legendasExtracao.domain.FaixaLegenda;
import org.traducao.projeto.legendasExtracao.domain.FormatoLegenda;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExtratorPgsStrategyTest {

    private final ExtratorPgsStrategy strategy = new ExtratorPgsStrategy();

    @Test
    void suportaApenasFormatoPgs() {
        assertThat(strategy.suporta(FormatoLegenda.PGS)).isTrue();
        assertThat(strategy.suporta(FormatoLegenda.ASS)).isFalse();
        assertThat(strategy.suporta(FormatoLegenda.SRT)).isFalse();
    }

    @Test
    void retornaVazioQuandoNaoHaFaixaPgs() {
        List<FaixaLegenda> faixas = List.of(
            new FaixaLegenda(0, "subtitles", "SubStation Alpha", "S_TEXT/ASS", "eng", "Dialogue", false, false)
        );

        assertThat(strategy.selecionarMelhorFaixa(faixas)).isEmpty();
    }

    @Test
    void preferePistaPadraoOuComIdiomaEsperado() {
        FaixaLegenda outraIdioma = new FaixaLegenda(0, "subtitles", "HDMV PGS", "S_HDMV/PGS", "spa", "Spanish", false, false);
        FaixaLegenda padrao = new FaixaLegenda(1, "subtitles", "HDMV PGS", "S_HDMV/PGS", "eng", "English", true, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(outraIdioma, padrao));

        assertThat(resultado).contains(padrao);
    }

    @Test
    void caiParaPrimeiraCandidataQuandoNenhumaEhPadraoOuEsperada() {
        FaixaLegenda primeira = new FaixaLegenda(0, "subtitles", "HDMV PGS", "S_HDMV/PGS", "spa", "Spanish", false, false);
        FaixaLegenda segunda = new FaixaLegenda(1, "subtitles", "HDMV PGS", "S_HDMV/PGS", "fra", "French", false, false);

        Optional<FaixaLegenda> resultado = strategy.selecionarMelhorFaixa(List.of(primeira, segunda));

        assertThat(resultado).contains(primeira);
    }
}
