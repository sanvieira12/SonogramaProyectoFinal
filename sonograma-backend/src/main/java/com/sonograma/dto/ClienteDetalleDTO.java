package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteDetalleDTO {
    private ClienteDTO cliente;
    private List<VentaResponseDTO> historialCompras;
    private List<DireccionClienteDTO> direcciones;
    private List<EnvioDTO> historialEnvios;
    private Long cantidadTotalCompras;
    private BigDecimal dineroTotalGastado;
    private BigDecimal promedioGastadoPorCompra;
    private BigDecimal mayorGastoCompraIndividual;
    private String generoMasComprado;
    private String decadaMusicalMasComprada;
    private String mesMasCompras;
    private LocalDateTime ultimaCompra;
}
