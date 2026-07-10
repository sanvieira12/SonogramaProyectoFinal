package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PricingMarkupUpdateRequestDTO(
    @NotNull(message = "El markup es obligatorio")
    @DecimalMin(value = "0.0001", inclusive = true, message = "El markup debe ser mayor a 0")
    BigDecimal markup
) {}
