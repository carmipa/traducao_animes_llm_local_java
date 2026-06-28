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
    void naoSinalizaAdjetivosInvariaveisComoErroDeGenero() {
        assertThat(detector.analisar("She is happy.", "Ela está feliz.").suspeito()).isFalse();
        assertThat(detector.analisar("The girl is sad.", "A garota triste.").suspeito()).isFalse();
        assertThat(detector.analisar("The boy is young.", "O garoto jovem.").suspeito()).isFalse();
        assertThat(detector.analisar("The woman is strong.", "A mulher forte.").suspeito()).isFalse();
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
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("masculino marcado") || m.contains("neutralizar"));

        resultado = detector.analisar("She looks tired.", "Ela parece cansado.");
        assertThat(resultado.suspeito()).isTrue();
        assertThat(resultado.motivos()).anyMatch(m -> m.contains("feminino") || m.contains("ela"));
    }

    @Test
    void detectaMasculinoPadraoQuandoInglesNaoMarcaGenero() {
        assertThat(detector.analisar("Are you ready?", "Você está pronto?").suspeito()).isTrue();
        assertThat(detector.analisar("Thank you.", "Obrigado.").suspeito()).isTrue();
        assertThat(detector.analisar("I'm not drunk.", "Não estou bêbado.").suspeito()).isTrue();
        assertThat(detector.analisar("Are you hurt?", "Estás ferido?").suspeito()).isTrue();
        assertThat(detector.analisar("I'm busy today.", "Estou ocupado hoje.").suspeito()).isTrue();
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
    void naoConfundeArtigoDepoisDePreposicaoComPronomeDeGenero() {
        var resultado = detector.analisar("I made this for her brother.", "Fiz isto para o irmão dela.");
        assertThat(resultado.suspeito()).isFalse();

        resultado = detector.analisar("He returned to the base.", "Ele voltou para a base.");
        assertThat(resultado.suspeito()).isFalse();
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
