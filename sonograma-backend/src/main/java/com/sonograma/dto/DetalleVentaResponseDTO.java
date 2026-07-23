package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVentaResponseDTO {
    private Long idDetalle;
    private Long idDisco;
    private String artista;
    private String album;
    private String descripcion;
    private String codigoInterno;
    private String imagenUrl;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal importeVentaReal;
    private BigDecimal gananciaNeta;
    /** Canonical name for the historical merchandise gross profit. */
    private BigDecimal grossProfit;
    private BigDecimal detailGrossProfit;
    private String estadoGanancia;
    private BigDecimal costoAdquisicionUyu;
    private String fuenteCostoAdquisicion;
    private String monedaCostoOriginal;
    private BigDecimal tipoCambioUsado;
    private Boolean costoCompleto;
    private Boolean manualItem;
}
