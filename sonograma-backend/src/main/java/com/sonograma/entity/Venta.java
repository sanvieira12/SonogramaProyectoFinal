package com.sonograma.entity;

import com.sonograma.enums.CanalVenta;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.MedioPago;
import com.sonograma.enums.TipoEntrega;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long idVenta;

    @ManyToOne
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_disco")
    private Disco disco;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<DetalleVenta> detalles = new ArrayList<>();

    @Column(name = "fecha_venta", nullable = false)
    @Builder.Default
    private LocalDateTime fechaVenta = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_venta")
    private CanalVenta canalVenta;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "costo_disco", precision = 10, scale = 2)
    private BigDecimal costoDisco;

    @Column(name = "precio_venta", precision = 10, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "costo_envio", precision = 10, scale = 2)
    private BigDecimal costoEnvio;

    @Column(name = "porcentaje_impuesto", precision = 5, scale = 2)
    private BigDecimal porcentajeImpuesto;

    @Column(name = "monto_impuesto", precision = 10, scale = 2)
    private BigDecimal montoImpuesto;

    @Column(name = "otros_costos", precision = 10, scale = 2)
    private BigDecimal otrosCostos;

    @Column(name = "total_final", precision = 10, scale = 2)
    private BigDecimal totalFinal;

    @Column(name = "ganancia_estimada", precision = 10, scale = 2)
    private BigDecimal gananciaEstimada;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entrega")
    private TipoEntrega tipoEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Builder.Default
    private EstadoVenta estado = EstadoVenta.PENDIENTE;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "numero_factura", unique = true)
    private String numeroFactura;

    @Column(name = "numero_recibo")
    private String numeroRecibo;

    @Column(name = "cliente_nombre_snapshot")
    private String clienteNombreSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago")
    private MedioPago medioPago;

    @Column(name = "monto_pagado", precision = 10, scale = 2)
    private BigDecimal montoPagado;

    @Column(name = "monto_deuda", precision = 10, scale = 2)
    private BigDecimal montoDeuda;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago")
    @Builder.Default
    private EstadoPago estadoPago = EstadoPago.PAGADO;

    @Column(name = "origen", length = 30)
    private String origen;

    @Column(name = "id_pre_venta_origen", unique = true)
    private Long idPreVentaOrigen;
}
