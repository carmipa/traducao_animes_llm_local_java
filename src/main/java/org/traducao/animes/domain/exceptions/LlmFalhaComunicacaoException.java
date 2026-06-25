package org.traducao.animes.domain.exceptions;

public class LlmFalhaComunicacaoException extends TradutorException {
    public LlmFalhaComunicacaoException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public LlmFalhaComunicacaoException(String message) {
        super(message);
    }
}
