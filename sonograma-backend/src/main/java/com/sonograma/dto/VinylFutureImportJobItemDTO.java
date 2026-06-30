package com.sonograma.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record VinylFutureImportJobItemDTO(
    int rowNumber,
    String codigoCatalogo,
    String artista,
    String album,
    BigDecimal unitCostEur,
    Integer quantity,
    BigDecimal lineTotalEur,
    String status,
    List<String> warnings,
    List<String> errors
) {
    public static VinylFutureImportJobItemDTO fromInvoiceItem(int rowNumber, InvoiceItem item) {
        return new VinylFutureImportJobItemDTO(
            rowNumber,
            item.codigoCatalogo(),
            item.artista(),
            item.album(),
            item.precioUnitario(),
            item.cantidad(),
            item.subtotal(),
            "PENDING",
            List.of(),
            List.of()
        );
    }

    public VinylFutureImportJobItemDTO withStatus(String nextStatus) {
        return new VinylFutureImportJobItemDTO(
            rowNumber,
            codigoCatalogo,
            artista,
            album,
            unitCostEur,
            quantity,
            lineTotalEur,
            nextStatus,
            warnings,
            errors
        );
    }

    public VinylFutureImportJobItemDTO addWarning(String warning) {
        List<String> next = new ArrayList<>(warnings == null ? List.of() : warnings);
        next.add(warning);
        return new VinylFutureImportJobItemDTO(
            rowNumber,
            codigoCatalogo,
            artista,
            album,
            unitCostEur,
            quantity,
            lineTotalEur,
            status,
            List.copyOf(next),
            errors
        );
    }

    public VinylFutureImportJobItemDTO addError(String error) {
        List<String> next = new ArrayList<>(errors == null ? List.of() : errors);
        next.add(error);
        return new VinylFutureImportJobItemDTO(
            rowNumber,
            codigoCatalogo,
            artista,
            album,
            unitCostEur,
            quantity,
            lineTotalEur,
            "FAILED",
            warnings,
            List.copyOf(next)
        );
    }
}
