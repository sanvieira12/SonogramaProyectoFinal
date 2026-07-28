package com.sonograma.exception;

/** A valid request cannot be completed because the current data state conflicts with it. */
public class ConflictoNegocioException extends RuntimeException {

    public ConflictoNegocioException(String mensaje) {
        super(mensaje);
    }
}
