package com.sonograma.entity;

import com.sonograma.enums.EstadoPago;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deuda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deuda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_deuda")
    private Long idDeuda;

    @OneToOne
    @JoinColumn(name = "id_venta", unique = true)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(name = "numero_factura")
    private String numeroFactura;

    @Column(name = "nombre_deudor_manual")
    private String nombreDeudorManual;

    @Column(name = "mail_manual")
    private String mailManual;

    @Column(name = "instagram_manual")
    private String instagramManual;

    @Column(name = "ci_manual")
    private String ciManual;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal montoPagado = BigDecimal.ZERO;

    /** Amount paid when the debt movement was created. */
    @Column(name = "monto_pagado_inicial", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal montoPagadoInicial = BigDecimal.ZERO;

    @Column(name = "monto_pendiente", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPendiente;

    @Column(name = "fecha_venta")
    private LocalDate fechaVenta;

    @Column(name = "fecha_deuda")
    private LocalDate fechaDeuda;

    @Column(name = "fecha_ultimo_pago")
    private LocalDate fechaUltimoPago;

    @Column(name = "fecha_creacion")
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false)
    @Builder.Default
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Column(name = "notas")
    private String notas;

    @Column(name = "activa", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean activa = true;
}
