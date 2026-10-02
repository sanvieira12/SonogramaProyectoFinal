package com.sonograma.dto;

public record ManualDiscogsExpectedCountRequestDTO(
        Integer expectedCopyCount,
        Long version,
        String note,
        String changeReason
) {}
