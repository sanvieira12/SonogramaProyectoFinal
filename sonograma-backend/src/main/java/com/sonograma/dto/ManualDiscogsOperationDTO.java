package com.sonograma.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Dedicated audit/read model; null fields remain truthful for historical operations. */
public record ManualDiscogsOperationDTO(
        UUID operationId,
        Long discogsReleaseId,
        String sourceCustomerCode,
        String normalizedSourceCustomerCode,
        String status,
        Integer requestedCopies,
        BigDecimal submittedPrice,
        String submittedCondition,
        Long batchId,
        Long resultProductId,
        List<Long> resultCopyIds,
        Boolean duplicateOverride,
        String duplicateOverrideReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime abandonedAt
) {}
