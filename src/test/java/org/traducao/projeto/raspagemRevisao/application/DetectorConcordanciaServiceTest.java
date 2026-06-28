package org.traducao.projeto.raspagemRevisao.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorConcordanciaServiceTest {

    private final DetectorConcordanciaService detector = new DetectorConcordanciaService();

    @Test
    void naoSinalizaTraducaoCorreta() {
        var resultado = detector.analisar("She is ready.", "Ela está pronta.");
        assertThat(resultado.suspeito()).isFalse();
    }

    @Test
    void detectaArtigoMasculinoComSubstantivoFeminino() {
        var resultado = detector.analisar("The girl is here.", "O garota está aqui.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("feminino"));
    }

    @Test
    void detectaParticipioMasculinoQuandoOriginalIndicaFeminino() {
        var resultado = detector.analisar("I'm tired.", "Estou cansado.");
        assertThat(resultado.suspeito()).isFalse();

        resultado = detector.analisar("She looks tired.", "Ela parece cansado.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("feminino") || m.contains("ela"));
    }

    @Test
    void detectaAdjetivoMasculinoAntesDeSubstantivoFeminino() {
        var resultado = detector.analisar("The goddess is angry.", "A deusa furioso.");
        assertThat(resultado.suspeito()).isTrue();
    }

    @Test
    void detectaPronomeObjetoErradoHerParaEle() {
        var resultado = detector.analisar("Tell her I'm coming.", "Diga a ele que estou indo.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("her") || m.contains("imperativo"));
    }

    @Test
    void detectaSujeitoEleQuandoOriginalDizShe() {
        var resultado = detector.analisar("She said nothing.", "Ele disse nada.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("'she'") || m.contains("ele"));
    }

    @Test
    void detectaEleFalaQuandoOriginalDizShe() {
        var resultado = detector.analisar("And then she speaks...", "Então ele fala...");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("ele"));
    }

    @Test
    void naoSinalizaEleComoObjetoQuandoOriginalDizSheSemReferenciaMasculina() {
        var resultado = detector.analisar("She told him to wait.", "Ela disse a ele para esperar.");
        assertThat(resultado.suspeito()).isFalse();

        resultado = detector.analisar("She saw him yesterday.", "Ela viu ele ontem.");
        assertThat(resultado.suspeito()).isFalse();
    }

    @Test
    void detectaTratamentoMasculinoComReferenciaFeminina() {
        var resultado = detector.analisar("Wait, girl!", "Espere, garoto!");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("Tratamento") || m.contains("vocativo"));
    }

    @Test
    void detectaElaComPredicadoMasculino() {
        var resultado = detector.analisar("She is angry.", "Ela está furioso.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("ela") || m.contains("masculino"));
    }

    @Test
    void detectaViEleQuandoOriginalDizHer() {
        var resultado = detector.analisar("I saw her yesterday.", "Eu vi ele ontem.");
        assertThat(resultado.suspeito()).isTrue();
    }

    @Test
    void ignoraTraducaoVazia() {
        assertThat(detector.analisar("Hello", null).suspeito()).isFalse();
        assertThat(detector.analisar("Hello", "   ").suspeito()).isFalse();
    }
}
