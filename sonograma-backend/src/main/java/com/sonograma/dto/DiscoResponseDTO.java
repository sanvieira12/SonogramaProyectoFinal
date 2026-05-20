package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoResponseDTO {
    private Long idDisco;
    private String codigoInterno;
    private String codigoQr;
    private String artista;
    private String album;
    private String genero;
    private Integer anio;
    private String condicion;
    private String tipoDisco;
    private BigDecimal costo;
    private BigDecimal precioVenta;
    private String estado;
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaActualizacion;
}
