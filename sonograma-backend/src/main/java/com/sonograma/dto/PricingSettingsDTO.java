package com.sonograma.dto;

import com.sonograma.enums.PricingRoundingRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PricingSettingsDTO(
    Long id,
    BigDecimal eurUyuRate,
    BigDecimal extraCostSingleEur,
    BigDecimal extraCostDoubleEur,
    BigDecimal extraCostMultiEur,
    BigDecimal markupSingle,
    BigDecimal markupDouble,
    BigDecimal markupMulti,
    PricingRoundingRule roundingRule,
    LocalDateTime updatedAt
) {}
