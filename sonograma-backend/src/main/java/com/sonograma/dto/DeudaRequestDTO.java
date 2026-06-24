package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeudaRequestDTO {
    private Long idCliente;
    private String nombreDeudorManual;
    private String mailManual;
    private String instagramManual;
    private String ciManual;
    private String descripcion;
    private String numeroFactura;
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private LocalDate fechaDeuda;
    private LocalDate fechaVenta;
    private String estadoPago;
    private String notas;
}
