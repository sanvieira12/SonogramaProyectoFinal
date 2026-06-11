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
public class ShippingOrderResponseDTO {
    private Long idShippingOrder;
    private String numero;
    private String proveedor;
    private LocalDate fechaOrden;
    private String estado;
    private BigDecimal costoTotal;
    private BigDecimal subtotal;
    private BigDecimal impuestos;
    private BigDecimal otrosCostos;
    private BigDecimal totalEstimado;
    private String notas;
    private LocalDateTime fechaCreacion;
    private List<ShippingOrderItemDTO> items;
}
