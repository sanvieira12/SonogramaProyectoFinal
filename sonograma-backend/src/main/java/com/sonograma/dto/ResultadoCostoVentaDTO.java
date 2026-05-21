package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoCostoVentaDTO {
    private BigDecimal costoDisco;
    private BigDecimal precioVenta;
    private BigDecimal costoEnvio;
    private BigDecimal porcentajeImpuesto;
    private BigDecimal montoImpuesto;
    private BigDecimal otrosCostos;
    private BigDecimal totalFinal;
    private BigDecimal gananciaEstimada;
}
