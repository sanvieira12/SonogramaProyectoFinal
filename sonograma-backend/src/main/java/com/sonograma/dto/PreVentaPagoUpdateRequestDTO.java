package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PreVentaPagoUpdateRequestDTO {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal precio;

    @NotNull
    @Min(1)
    private Integer cantidad;

    @NotNull
    private LocalDateTime fechaPago;

    private String medioPago;
    private String numeroRecibo;
    private String observaciones;
}
