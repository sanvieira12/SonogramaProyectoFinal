package com.sonograma.dto;

import com.sonograma.enums.ClasificacionItemVenta;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaDTO {
    private Long idDetalle;
    private Long idDisco;
    private String artista;
    private String album;
    private String descripcion;
    private String codigo;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private Boolean manualItem;
    private Long copyId;
    private String codigoQr;
    private ClasificacionItemVenta clasificacionItem;
}
