package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeudaResponseDTO {
    private Long idDeuda;
    private Long idVenta;
    private String numeroFactura;
    private Long idCliente;
    private String nombreCliente;
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private BigDecimal montoPendiente;
    private LocalDate fechaVenta;
    private LocalDate fechaUltimoPago;
    private LocalDateTime fechaCreacion;
    private String estadoPago;
    private String notas;
}
