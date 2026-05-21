package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionEnvioDTO {
    private String proveedor;
    private String departamento;
    private String sucursalCodigo;
    private String sucursalNombre;
    private BigDecimal costoEstimado;
    private String moneda;
}
