package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreVentaRequestDTO {
    @NotNull
    private Long idCliente;
    private Long idDisco;
    private String descripcion;
    private String codigoDisco;
    @NotNull
    private Integer cantidad;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal precio;
    private LocalDate fecha;
    private String estado;
    private String notas;
}
