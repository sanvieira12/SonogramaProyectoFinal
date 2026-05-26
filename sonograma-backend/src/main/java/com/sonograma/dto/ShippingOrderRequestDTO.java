package com.sonograma.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrderRequestDTO {
    private String proveedor;
    private LocalDate fechaOrden;
    private String notas;
    private List<ShippingOrderItemDTO> items;
}
