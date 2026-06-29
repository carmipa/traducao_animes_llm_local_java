package org.traducao.projeto.analisadorMidia.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.traducao.projeto.analisadorMidia.domain.exceptions.AnaliseStreamException;

import static org.assertj.core.api.Assertions.assertThat;

class AnalisadorMidiaExceptionsTest {

    @Test
    @DisplayName("Deve validar a hierarquia e metadados das exceções do analisador de mídia")
    void deveValidarAnalisadorExceptions() {
        AnalisadorException baseEx = new AnalisadorException("Falha de análise");
        assertThat(baseEx.getErrorId()).isNotNull();
        assertThat(baseEx.getTimestamp()).isNotNull();

        AnaliseStreamException streamEx = new AnaliseStreamException("Stream ilegível");
        assertThat(streamEx).isInstanceOf(AnalisadorException.class);
        assertThat(streamEx.getErrorId()).isNotNull();
    }
}
