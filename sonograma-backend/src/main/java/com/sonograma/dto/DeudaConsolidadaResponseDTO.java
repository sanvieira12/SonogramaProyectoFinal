package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One active debt-list row per customer, while retaining every movement. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeudaConsolidadaResponseDTO {
    /** Representative movement id, retained for backwards-compatible clients. */
    private Long idDeuda;
    private String grupoKey;
    private Long idCliente;
    private String nombreCliente;
    private String nombreDeudorManual;
    private String mailManual;
    private String instagramManual;
    private String ciManual;
    private BigDecimal montoTotal;
    private BigDecimal montoPagado;
    private BigDecimal montoPendiente;
    private LocalDate fechaVenta;
    private LocalDate fechaDeuda;
    private LocalDate fechaUltimoPago;
    private LocalDateTime fechaCreacion;
    private LocalDateTime updatedAt;
    private String estadoPago;
    private int cantidadMovimientos;
    private List<DeudaResponseDTO> movimientos;
}
