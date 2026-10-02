package com.sonograma.dto;

import com.sonograma.enums.ManualDiscogsReconciliationStatus;

import java.time.LocalDateTime;

public record ManualDiscogsSourceReconciliationDTO(
        String sourceCustomerCode,
        String normalizedSourceCustomerCode,
        Integer expectedCopyCount,
        long provablePhysicalCopyCount,
        long availableCopyCount,
        long soldCopyCount,
        long removedCopyCount,
        long distinctReleaseCount,
        long duplicateReleaseGroupCount,
        long extraDuplicateCopyCount,
        long pendingOperationCount,
        long completedOperationCount,
        long abandonedOperationCount,
        Long difference,
        ManualDiscogsReconciliationStatus reconciliationStatus,
        String reconciliationNote,
        String expectedCountChangeReason,
        String updatedBy,
        LocalDateTime updatedAt,
        Long version
) {}
