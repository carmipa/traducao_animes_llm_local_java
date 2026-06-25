package org.traducao.animes.application;

import org.junit.jupiter.api.Test;
import org.traducao.animes.domain.exceptions.AlucinacaoDetectadaException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorTraducaoServiceTest {

    private final ValidadorTraducaoService validador = new ValidadorTraducaoService();

    @Test
    void aceitaTraducaoLimpaEmPortugues() {
        assertThatCode(() -> validador.validarFala("Olá, mundo! Vamos lutar juntos."))
            .doesNotThrowAnyException();
    }

    @Test
    void aceitaNuloEVazioSemLancar() {
        assertThatCode(() -> validador.validarFala(null)).doesNotThrowAnyException();
        assertThatCode(() -> validador.validarFala("   ")).doesNotThrowAnyException();
    }

    @Test
    void detectaResiduoEmIngles() {
        assertThatThrownBy(() -> validador.validarFala("Eu sei what you fizeram"))
            .isInstanceOf(AlucinacaoDetectadaException.class);
    }


    @Test
    void detectaPreambuloTipico() {
        assertThatThrownBy(() -> validador.validarFala("Aqui está a tradução: Olá!"))
            .isInstanceOf(AlucinacaoDetectadaException.class);
    }

    @Test
    void naoFalsoPositivaEmPalavrasPortuguesasParecidas() {
        assertThatCode(() -> validador.validarFala("Abaixo a tirania dos Zeon!"))
            .doesNotThrowAnyException();
    }
}
