package com.sonograma.exception;

public class ManualDiscogsPendingFinalizationException extends ConflictoNegocioException {
    private final long pendingCount;

    public ManualDiscogsPendingFinalizationException(long pendingCount) {
        super("Hay " + pendingCount + " importaciones pendientes.");
        this.pendingCount = pendingCount;
    }

    public long getPendingCount() { return pendingCount; }
}
