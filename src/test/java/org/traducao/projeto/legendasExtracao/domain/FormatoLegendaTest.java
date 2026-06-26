package org.traducao.projeto.legendasExtracao.domain;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.legendasExtracao.domain.FormatoLegenda;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoLegendaTest {

    @Test
    void retornaAssQuandoEntradaNulaOuVazia() {
        assertThat(FormatoLegenda.fromString(null)).isEqualTo(FormatoLegenda.ASS);
        assertThat(FormatoLegenda.fromString("")).isEqualTo(FormatoLegenda.ASS);
        assertThat(FormatoLegenda.fromString("   ")).isEqualTo(FormatoLegenda.ASS);
    }

    @Test
    void reconhecePgsESrtIndependenteDeCaixa() {
        assertThat(FormatoLegenda.fromString("pgs")).isEqualTo(FormatoLegenda.PGS);
        assertThat(FormatoLegenda.fromString("PGS")).isEqualTo(FormatoLegenda.PGS);
        assertThat(FormatoLegenda.fromString("srt")).isEqualTo(FormatoLegenda.SRT);
        assertThat(FormatoLegenda.fromString("SRT")).isEqualTo(FormatoLegenda.SRT);
    }

    @Test
    void caiParaAssQuandoValorDesconhecido() {
        assertThat(FormatoLegenda.fromString("xyz")).isEqualTo(FormatoLegenda.ASS);
        assertThat(FormatoLegenda.fromString("ass")).isEqualTo(FormatoLegenda.ASS);
    }
}
