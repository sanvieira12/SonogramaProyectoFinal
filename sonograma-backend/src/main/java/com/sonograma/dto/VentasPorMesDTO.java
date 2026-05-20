package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentasPorMesDTO {
    private String mes;
    private String etiqueta;
    private Long cantidad;
    private BigDecimal totalMonto;
}
