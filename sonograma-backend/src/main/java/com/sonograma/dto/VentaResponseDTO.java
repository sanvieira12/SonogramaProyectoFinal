package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private BigDecimal costoDisco;
    private BigDecimal precioVenta;
    private BigDecimal costoEnvio;
    private BigDecimal porcentajeImpuesto;
    private BigDecimal montoImpuesto;
    private BigDecimal otrosCostos;
    private BigDecimal totalFinal;
    private BigDecimal gananciaEstimada;
    private String tipoEntrega;
    private String estado;
    private String observaciones;
    private EnvioDTO envio;
    private String numeroFactura;
    private String clienteNombreSnapshot;
    private String medioPago;
    private BigDecimal montoPagado;
    private BigDecimal montoDeuda;
    private String estadoPago;
    private BigDecimal subtotal;
    private BigDecimal descuentoPorcentaje;
    private List<DetalleVentaResponseDTO> detalles;
}
