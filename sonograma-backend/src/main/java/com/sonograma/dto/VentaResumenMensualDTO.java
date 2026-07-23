package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaResumenMensualDTO {
    private Long idVenta;
    private LocalDateTime fecha;
    private String cliente;
    private String numeroRecibo;
    private String estadoPago;
    private BigDecimal totalVenta;
    private BigDecimal montoRecibido;
    private BigDecimal deudaPendiente;
    private BigDecimal gananciaNeta;
    private BigDecimal grossProfit;
    private BigDecimal saleGrossProfit;
    private Boolean grossProfitAvailable;
    private String grossProfitUnavailableReason;
    private String estadoGanancia;
}
