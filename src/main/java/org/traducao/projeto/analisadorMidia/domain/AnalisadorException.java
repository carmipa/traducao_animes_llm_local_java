package org.traducao.projeto.analisadorMidia.domain;

public class AnalisadorException extends RuntimeException {
    public AnalisadorException(String message) {
        super(message);
    }

    public AnalisadorException(String message, Throwable cause) {
        super(message, cause);
    }
}
