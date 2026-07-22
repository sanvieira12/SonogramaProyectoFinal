package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemResumenMensualDTO {
    private Long idVenta;
    private String artista;
    private String album;
    private String codigoInterno;
    private Integer cantidad;
    private BigDecimal importeVentaReal;
    private BigDecimal costoAdquisicionOriginal;
    private BigDecimal gananciaNeta;
    private String estadoGanancia;
}
