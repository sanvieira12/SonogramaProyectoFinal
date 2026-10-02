package com.sonograma.dto;

import java.util.List;

public record ManualDiscogsDuplicateConflictDTO(
        String code,
        String message,
        String sourceCustomerCode,
        Long discogsReleaseId,
        List<ManualDiscogsDuplicateCopyDTO> existingCopies
) {}
