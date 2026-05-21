package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvioDTO {
    private Long idEnvio;
    private String direccionEnvio;
    private String departamento;
    private String sucursalDacCodigo;
    private String sucursalDacNombre;
    private BigDecimal costoEnvio;
    private String estadoLogistico;
    private String numeroSeguimiento;
    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaEntrega;
}
