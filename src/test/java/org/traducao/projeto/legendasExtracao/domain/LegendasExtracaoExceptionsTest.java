package org.traducao.projeto.legendasExtracao.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.traducao.projeto.legendasExtracao.domain.exceptions.FormatoLegendaInvalidoException;

import static org.assertj.core.api.Assertions.assertThat;

class LegendasExtracaoExceptionsTest {

    @Test
    @DisplayName("Deve validar a hierarquia e metadados das exceções de extração de legendas")
    void deveValidarExtratorExceptions() {
        ExtratorException baseEx = new ExtratorException("Falha na extração");
        assertThat(baseEx.getErrorId()).isNotNull();
        assertThat(baseEx.getTimestamp()).isNotNull();

        FormatoLegendaInvalidoException fmtEx = new FormatoLegendaInvalidoException("Formato desconhecido");
        assertThat(fmtEx).isInstanceOf(ExtratorException.class);
        assertThat(fmtEx.getErrorId()).isNotNull();
    }
}
