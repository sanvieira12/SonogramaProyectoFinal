package com.sonograma.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvioDTO {
    private Long idEnvio;
    private String direccionEnvio;
    private String estadoLogistico;
    private String numeroSeguimiento;
    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaEntrega;
}
