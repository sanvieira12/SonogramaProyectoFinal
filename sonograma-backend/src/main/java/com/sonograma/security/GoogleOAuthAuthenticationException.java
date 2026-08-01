package com.sonograma.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GoogleOAuthAuthenticationException extends RuntimeException {

    private final HttpStatus status;
    private final String safeMessage;

    public GoogleOAuthAuthenticationException(HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.status = status;
        this.safeMessage = safeMessage;
    }
}
