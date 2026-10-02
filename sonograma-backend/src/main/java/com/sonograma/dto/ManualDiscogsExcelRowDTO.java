package com.sonograma.dto;

import com.sonograma.enums.EstadoCopiaDisco;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Flat source-export row fetched in one query without loading catalogue graphs. */
public record ManualDiscogsExcelRowDTO(
        Long copyId,
        LocalDateTime copyCreatedAt,
        BigDecimal copyPrice,
        String copyCondition,
        EstadoCopiaDisco copyState,
        String normalizedSourceCustomerCode,
        Long discogsReleaseId,
        String storedDiscogsUrl,
        String artist,
        String title,
        String genre
) {}
