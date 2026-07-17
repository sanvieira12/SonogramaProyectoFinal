package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeudaResponseDTO {
    private Long idDeuda;
    private Long idVenta;
    private String numeroFactura;
    private String numeroRecibo;
    private Long idCliente;
    private String nombreCliente;
    private String nombreDeudorManual;
    private String mailManual;
    private String instagramManual;
    private String ciManual;
    private String descripcion;
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private BigDecimal montoPendiente;
    private LocalDate fechaVenta;
    private LocalDate fechaDeuda;
    private LocalDate fechaUltimoPago;
    private LocalDateTime fechaCreacion;
    private LocalDateTime updatedAt;
    private String estadoPago;
    private String notas;
    private List<PagoDeudaDTO> pagos;
    private List<DetalleVentaResponseDTO> detalles;
}
