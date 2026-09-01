package com.sonograma.dto;

public record VinylFutureManualImportResultDTO(
    String previewId,
    Long productId,
    String catalogueStatus,
    int addedCopies,
    int resultingStock,
    boolean pendingItemResolved,
    boolean alreadyProcessed
) {}
