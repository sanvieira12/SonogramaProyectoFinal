package com.sonograma.dto;

import java.math.BigDecimal;

/** Minimal available-copy choice prepared for the later exact-sale phase. */
public record DiscoSaleSearchCopyDTO(
        Long copyId,
        Integer copyNumber,
        String codigoQr,
        BigDecimal precioVenta,
        String condicionFisica,
        String sourceCustomerCode,
        String normalizedSourceCustomerCode,
        Long manualBatchId,
        String estado
) {}
