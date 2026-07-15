package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadisticaItemDTO {
    private String clave;
    private String etiqueta;
    private Long cantidad;
    private Long cantidadPagosDeuda;
    private BigDecimal totalMonto;
    private BigDecimal gananciaEstimada;
}
