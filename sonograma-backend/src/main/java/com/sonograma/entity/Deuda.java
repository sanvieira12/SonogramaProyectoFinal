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
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "monto_pendiente", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPendiente;

    @Column(name = "fecha_venta")
    private LocalDate fechaVenta;

    @Column(name = "fecha_ultimo_pago")
    private LocalDate fechaUltimoPago;

    @Column(name = "fecha_creacion")
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false)
    @Builder.Default
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Column(name = "notas")
    private String notas;
}
