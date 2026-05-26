package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrderItemDTO {
    private Long idShippingOrderItem;
    private Long idDisco;
    private String artista;
    private String album;
    private String descripcion;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
}
