package com.sonograma.dto;

import java.util.List;

public record ManualDiscogsFinalizationConflictDTO(
        String code,
        String message,
        List<String> warnings,
        ManualDiscogsSourceReconciliationDTO reconciliation
) {}
