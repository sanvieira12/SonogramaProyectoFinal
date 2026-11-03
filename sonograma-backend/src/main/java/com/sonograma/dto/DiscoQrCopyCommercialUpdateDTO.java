package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Exact-copy commercial fields. Null means "leave unchanged"; blank condition clears it. */
public record DiscoQrCopyCommercialUpdateDTO(
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
        BigDecimal precioVenta,
        @Size(max = 50, message = "La condición física no puede superar 50 caracteres")
        String condicionFisica
) {
}
