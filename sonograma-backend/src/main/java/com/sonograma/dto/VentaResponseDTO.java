package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaResponseDTO {
    private Long idVenta;
    private Long idCliente;
    private String nombreCliente;
    private String apellidoCliente;
    private Long idDisco;
    private String artista;
    private String album;
    private LocalDateTime fechaVenta;
    private String canalVenta;
    private BigDecimal total;
    private String tipoEntrega;
    private String estado;
    private String observaciones;
    private EnvioDTO envio;
}
