package com.sonograma.dto;

import java.time.LocalDate;
import java.util.List;

public record VinylFutureInvoiceValidationDTO(
    String validationId,
    String invoiceNumber,
    LocalDate invoiceDate,
    Integer declaredQuantity,
    int detectedSourceRows,
    int parsedRows,
    int unparsedRows,
    int parsedPhysicalQuantity,
    int pendingPhysicalQuantity,
    boolean consistent,
    List<String> warnings,
    List<String> errors,
    List<InvoiceSourceRowDTO> sourceRows,
    List<InvoiceProductConsolidationDTO> consolidations
) {
    public VinylFutureInvoiceValidationDTO withValidationId(String value) {
        return new VinylFutureInvoiceValidationDTO(
            value, invoiceNumber, invoiceDate, declaredQuantity, detectedSourceRows,
            parsedRows, unparsedRows, parsedPhysicalQuantity, pendingPhysicalQuantity,
            consistent, warnings, errors, sourceRows, consolidations
        );
    }

    public boolean requiresReview() {
        return !consistent || unparsedRows > 0 || !errors.isEmpty();
    }
}
