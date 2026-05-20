package com.sonograma.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDTO {
    private Long idVenta;
    private Long idCliente;
    private String nombreCliente;
    private Long idDisco;
    private String artistaDisco;
    private String albumDisco;
    private LocalDateTime fechaVenta;
    private String canalVenta;
    private BigDecimal total;
    private String tipoEntrega;
    private String estado;
    private String observaciones;
}
