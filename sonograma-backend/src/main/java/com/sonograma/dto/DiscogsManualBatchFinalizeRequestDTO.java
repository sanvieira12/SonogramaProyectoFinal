package com.sonograma.dto;

/** Required payload used when an open manual Discogs batch is finalized. */
public record DiscogsManualBatchFinalizeRequestDTO(
        Integer porcentajeSonograma,
        Boolean confirmPendingOperations,
        Boolean confirmReconciliationWarnings
) {
    public DiscogsManualBatchFinalizeRequestDTO(Integer porcentajeSonograma) {
        this(porcentajeSonograma, false, false);
    }

    /** Compatibility constructor: Phase 5's single confirmation now confirms the combined warning. */
    public DiscogsManualBatchFinalizeRequestDTO(Integer porcentajeSonograma, Boolean confirmWarnings) {
        this(porcentajeSonograma, confirmWarnings, confirmWarnings);
    }
}
