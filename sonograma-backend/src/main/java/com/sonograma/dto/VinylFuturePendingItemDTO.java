package com.sonograma.dto;

public record VinylFuturePendingItemDTO(
    Long pendingItemId,
    Long orderId,
    String invoiceNumber,
    Integer pageNumber,
    String sourceText,
    String reviewReason,
    Integer estimatedQuantity
) {}
