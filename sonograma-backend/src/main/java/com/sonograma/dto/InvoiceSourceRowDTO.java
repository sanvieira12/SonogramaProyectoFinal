package com.sonograma.dto;

public record InvoiceSourceRowDTO(
    int sourceRowNumber,
    int pageNumber,
    String sourceText,
    String status,
    Integer estimatedQuantity,
    String reason,
    InvoiceItem parsedItem
) {}
