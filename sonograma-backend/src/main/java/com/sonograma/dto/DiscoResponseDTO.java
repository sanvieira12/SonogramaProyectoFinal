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
    private String selloDiscografico;
    private String descripcion;
    private Integer anio;
    private String condicion;
    private String tipoDisco;
    private BigDecimal costo;
    private BigDecimal precioVenta;
    private String estado;
    private String pais;
    private String estilo;
    private String tracklist;
    private String notas;
    private String procedencia;
    private String imagenUrl;
    private String previewUrl;
    private String discogsUrl;
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaActualizacion;
}
