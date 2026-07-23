package com.sonograma.dto;

import com.sonograma.enums.CategoriaGasto;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoTiendaRequestDTO {
    private LocalDate fecha;
    private String descripcion;
    private BigDecimal monto;

    @NotNull(message = "La categoría es obligatoria")
    private CategoriaGasto categoria;
}
