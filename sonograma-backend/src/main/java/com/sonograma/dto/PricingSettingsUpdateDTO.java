package com.sonograma.dto;

import com.sonograma.enums.PricingRoundingRule;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record PricingSettingsUpdateDTO(
    @DecimalMin(value = "0.0001", inclusive = true, message = "La cotización EUR/UYU debe ser mayor a 0")
    BigDecimal eurUyuRate,
    @DecimalMin(value = "0.0", inclusive = true, message = "El extra de single no puede ser negativo")
    BigDecimal extraCostSingleEur,
    @DecimalMin(value = "0.0", inclusive = true, message = "El extra de double no puede ser negativo")
    BigDecimal extraCostDoubleEur,
    @DecimalMin(value = "0.0", inclusive = true, message = "El extra de multi no puede ser negativo")
    BigDecimal extraCostMultiEur,
    @DecimalMin(value = "0.0001", inclusive = true, message = "El markup de single debe ser mayor a 0")
    BigDecimal markupSingle,
    @DecimalMin(value = "0.0001", inclusive = true, message = "El markup de double debe ser mayor a 0")
    BigDecimal markupDouble,
    @DecimalMin(value = "0.0001", inclusive = true, message = "El markup de multi debe ser mayor a 0")
    BigDecimal markupMulti,
    PricingRoundingRule roundingRule
) {}
