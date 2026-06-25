package org.traducao.animes.domain.exceptions;

public class TradutorException extends RuntimeException {
    public TradutorException(String message) {
        super(message);
    }
    
    public TradutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
