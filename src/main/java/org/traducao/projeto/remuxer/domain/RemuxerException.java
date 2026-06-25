package org.traducao.projeto.remuxer.domain;

public class RemuxerException extends RuntimeException {
    public RemuxerException(String message) {
        super(message);
    }

    public RemuxerException(String message, Throwable cause) {
        super(message, cause);
    }
}
