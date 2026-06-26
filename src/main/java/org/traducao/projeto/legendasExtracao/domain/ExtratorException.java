package org.traducao.projeto.legendasExtracao.domain;

public class ExtratorException extends RuntimeException {
    public ExtratorException(String message) {
        super(message);
    }

    public ExtratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
