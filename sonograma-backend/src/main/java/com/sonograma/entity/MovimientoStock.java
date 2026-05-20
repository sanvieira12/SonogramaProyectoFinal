package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_disco", nullable = false)
    private Disco disco;

    // INGRESO, VENTA, RESERVA, AJUSTE
    @Column(name = "tipo_movimiento", nullable = false)
    private String tipoMovimiento;

    @Column(name = "cantidad")
    private Integer cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuarioOperador;

    @Column(name = "fecha_movimiento")
    @Builder.Default
    private LocalDateTime fechaMovimiento = LocalDateTime.now();

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;
}
