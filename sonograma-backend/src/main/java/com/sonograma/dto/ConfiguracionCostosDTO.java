package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionCostosDTO {
    private BigDecimal porcentajeImpuesto;
    private BigDecimal otrosCostos;
    private String moneda;
}
