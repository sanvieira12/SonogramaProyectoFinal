package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreVentaResponseDTO {
    private Long idPreVenta;
    private Long idCliente;
    private String clienteNombre;
    private Long idDisco;
    private String artista;
    private String album;
    private String descripcion;
    private String codigoDisco;
    private Long idVentaPago;
    private java.time.LocalDateTime fechaPago;
    private Integer cantidad;
    private BigDecimal precio;
    private LocalDate fecha;
    private String estado;
    private String notas;
}
