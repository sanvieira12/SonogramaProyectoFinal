package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoTiendaDTO {
    private Long idGasto;
    private LocalDate fecha;
    private String descripcion;
    private BigDecimal monto;
}
