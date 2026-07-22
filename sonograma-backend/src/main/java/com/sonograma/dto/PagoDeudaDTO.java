package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoDeudaDTO {
    private Long idPagoDeuda;
    private BigDecimal monto;
    private LocalDate fechaPago;
    private String notas;
    private String numeroRecibo;
    private LocalDateTime createdAt;
}
