package com.sonograma.dto;

public record ManualDiscogsPendingFinalizationConflictDTO(
        String code,
        String message,
        long pendingCount
) {}
