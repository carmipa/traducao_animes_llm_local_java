package org.traducao.projeto.traducao.infrastructure.legenda;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MascaradorTagsTest {

    private final MascaradorTags mascarador = new MascaradorTags();

    @Test
    void mascaraTagDeEstiloEDesmascaraDeVolta() {
        String original = "{\\i1}Space.";
        MascaradorTags.Mascarado mascarado = mascarador.mascarar(original);

        assertThat(mascarado.texto()).isEqualTo("[[TAG0]]Space.");
        assertThat(mascarado.tags()).containsExactly("{\\i1}");

        String traduzido = mascarador.desmascarar("[[TAG0]]Espaço.", mascarado.tags());
        assertThat(traduzido).isEqualTo("{\\i1}Espaço.");
    }

    @Test
    void mascaraQuebraDeLinhaForcada() {
        String original = "Linha um\\NLinha dois";
        MascaradorTags.Mascarado mascarado = mascarador.mascarar(original);

        assertThat(mascarado.tags()).containsExactly("\\N");

        String traduzido = mascarador.desmascarar("Linha 1[[TAG0]]Linha 2", mascarado.tags());
        assertThat(traduzido).isEqualTo("Linha 1\\NLinha 2");
    }

    @Test
    void textoSemTagsNaoEAlterado() {
        MascaradorTags.Mascarado mascarado = mascarador.mascarar("Texto simples.");

        assertThat(mascarado.texto()).isEqualTo("Texto simples.");
        assertThat(mascarado.tags()).isEmpty();
        assertThat(mascarador.desmascarar("Texto simples.", mascarado.tags())).isEqualTo("Texto simples.");
    }

    @Test
    void multiplasTagsNaMesmaFala() {
        String original = "{\\i1}Olá {\\i0}mundo";
        MascaradorTags.Mascarado mascarado = mascarador.mascarar(original);

        assertThat(mascarado.tags()).containsExactly("{\\i1}", "{\\i0}");
        assertThat(mascarado.texto()).isEqualTo("[[TAG0]]Olá [[TAG1]]mundo");

        String traduzido = mascarador.desmascarar("[[TAG0]]Hello [[TAG1]]world", mascarado.tags());
        assertThat(traduzido).isEqualTo("{\\i1}Hello {\\i0}world");
    }

    @Test
    void falhaAltoQuandoLlmPerdeUmMarcador() {
        MascaradorTags.Mascarado mascarado = mascarador.mascarar("{\\i1}Olá");

        assertThatThrownBy(() -> mascarador.desmascarar("Hello", mascarado.tags()))
            .isInstanceOf(AlucinacaoDetectadaException.class);
    }

    @Test
    void falhaAltoQuandoLlmDuplicaMarcador() {
        String original = "{\\i1}Olá {\\i0}mundo";
        MascaradorTags.Mascarado mascarado = mascarador.mascarar(original);

        assertThatThrownBy(() -> mascarador.desmascarar("[[TAG0]][[TAG0]]Hello world", mascarado.tags()))
            .isInstanceOf(AlucinacaoDetectadaException.class);
    }

    @Test
    void falhaAltoQuandoLlmInventaMarcadorInexistente() {
        MascaradorTags.Mascarado mascarado = mascarador.mascarar("Texto sem tags");

        assertThatThrownBy(() -> mascarador.desmascarar("[[TAG0]]Texto inventado", mascarado.tags()))
            .isInstanceOf(AlucinacaoDetectadaException.class);
    }

    @Test
    void ignoraTypesettingComClipPesadoMesmoQuandoSobraTextoCurto() {
        String texto = "{\\shad0\\pos(1561.5,819.5)\\c&H000000&\\blur0.5\\iclip(m 1514.446 806.703 b "
            + "1518.071 806.703 1520.914 805.766 1522.977 803.5 1524.961 801.328 1526.258 797.875 "
            + "1526.258 792.609 1526.258 787.328 1524.852 778.531 1514.446 778.531 1504.883 778.531 "
            + "1502.727 786.141 1502.727 792.609 1502.727 797.875 1504.024 801.328)}Orario";

        assertThat(mascarador.contemTextoTraduzivel(texto)).isFalse();
    }
}
