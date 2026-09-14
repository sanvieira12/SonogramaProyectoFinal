package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoDeudaUpdateRequest {
    private BigDecimal monto;
    private LocalDate fechaPago;
    private String notas;
    private String numeroRecibo;
}
