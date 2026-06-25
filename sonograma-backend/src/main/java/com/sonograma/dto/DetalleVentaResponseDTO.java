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
    private String codigoInterno;
    private String imagenUrl;
    private BigDecimal precioUnitario;
}
