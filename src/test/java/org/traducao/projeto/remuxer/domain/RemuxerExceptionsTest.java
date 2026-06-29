package org.traducao.projeto.remuxer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemuxerExceptionsTest {

    @Test
    @DisplayName("Deve validar a hierarquia e metadados das exceções do Remuxer")
    void deveValidarRemuxerExceptions() {
        RemuxerException baseEx = new RemuxerException("Falha no remux");
        assertThat(baseEx.getErrorId()).isNotNull();
        assertThat(baseEx.getTimestamp()).isNotNull();

        MkvToolNixNaoEncontradoException mkvEx = new MkvToolNixNaoEncontradoException("mkvmerge não encontrado");
        assertThat(mkvEx).isInstanceOf(RemuxerException.class);
        assertThat(mkvEx.getErrorId()).isNotNull();
    }
}
