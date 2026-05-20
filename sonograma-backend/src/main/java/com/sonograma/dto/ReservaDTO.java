package com.sonograma.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaDTO {
    private Long idReserva;
    private Long idCliente;
    private String nombreCliente;
    private Long idDisco;
    private String artistaDisco;
    private String albumDisco;
    private LocalDateTime fechaReserva;
    private LocalDateTime fechaVencimiento;
    private BigDecimal senia;
    private String estado;
}
