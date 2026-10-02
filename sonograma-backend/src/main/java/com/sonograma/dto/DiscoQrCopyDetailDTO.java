package com.sonograma.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only physical-copy projection used by the Catalog detail surface. */
public record DiscoQrCopyDetailDTO(
        Long id,
        Long productId,
        Integer copyNumber,
        String codigoQr,
        String estado,
        BigDecimal precioVenta,
        String condicionFisica,
        LocalDateTime createdAt,
        Long manualBatchId,
        String sourceCustomerCode,
        String normalizedSourceCustomerCode,
        String dispositionReason,
        String dispositionNote,
        LocalDateTime disposedAt,
        String disposedBy,
        LocalDateTime updatedAt
) {}
