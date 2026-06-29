package org.traducao.projeto.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasePipelineExceptionTest {

    private static class DummyPipelineException extends BasePipelineException {
        public DummyPipelineException(String message) {
            super(message);
        }

        public DummyPipelineException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Test
    @DisplayName("Deve inicializar errorId e timestamp ao instanciar exceção base")
    void deveInicializarErrorIdETimestamp() {
        DummyPipelineException ex = new DummyPipelineException("Erro de teste");

        assertThat(ex.getMessage()).isEqualTo("Erro de teste");
        assertThat(ex.getErrorId()).isNotNull().isNotBlank();
        assertThat(ex.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Deve preservar a causa raiz e metadados ao capturar exceções encadeadas")
    void devePreservarCausaRaiz() {
        RuntimeException cause = new RuntimeException("Causa original");
        DummyPipelineException ex = new DummyPipelineException("Erro envelopado", cause);

        assertThat(ex.getMessage()).isEqualTo("Erro envelopado");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getErrorId()).isNotNull();
        assertThat(ex.getTimestamp()).isNotNull();
    }
}
