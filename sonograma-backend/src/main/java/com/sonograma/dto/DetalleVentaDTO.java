package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaDTO {
    private Long idDisco;
    private String artista;
    private String album;
    private String descripcion;
    private String codigo;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private Boolean manualItem;
}
