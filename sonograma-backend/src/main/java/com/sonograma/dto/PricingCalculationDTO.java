package com.sonograma.dto;

import java.math.BigDecimal;

public record PricingCalculationDTO(
    String recordType,
    BigDecimal unitLineTotalEur,
    BigDecimal extraCostEur,
    BigDecimal realUnitCostEur,
    BigDecimal realUnitCostUyu,
    BigDecimal markup,
    BigDecimal finalPriceUyu
) {}
