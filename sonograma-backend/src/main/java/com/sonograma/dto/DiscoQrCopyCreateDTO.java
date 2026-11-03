package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Manual receipt of one exact USED copy outside supplier imports. */
public record DiscoQrCopyCreateDTO(
        @NotNull(message = "El precio de la copia es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
        BigDecimal precioVenta,
        @NotBlank(message = "La condición física es obligatoria")
        @Size(max = 50, message = "La condición física no puede superar 50 caracteres")
        String condicionFisica
) {
}
