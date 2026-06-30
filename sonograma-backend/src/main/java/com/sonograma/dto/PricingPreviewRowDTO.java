package com.sonograma.dto;

public record PricingPreviewRowDTO(
    Long idDisco,
    String invoiceNumber,
    java.time.LocalDate invoiceDate,
    String supplier,
    java.math.BigDecimal shipping,
    String code,
    String artist,
    String title,
    String format,
    String type,
    java.math.BigDecimal unitPriceEur,
    Integer quantity,
    java.math.BigDecimal unitLineTotalEur,
    java.math.BigDecimal extraCostEur,
    java.math.BigDecimal realCostEur,
    java.math.BigDecimal realCostUyu,
    java.math.BigDecimal markup,
    java.math.BigDecimal finalSalePriceUyu,
    String pricingMode
) {}
