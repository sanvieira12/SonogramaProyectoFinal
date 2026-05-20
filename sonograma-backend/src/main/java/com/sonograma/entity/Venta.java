package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    @JoinColumn(name = "id_disco", nullable = false)
    private Disco disco;

    @Column(name = "fecha_venta", nullable = false)
    @Builder.Default
    private LocalDateTime fechaVenta = LocalDateTime.now();

    // LOCAL, INSTAGRAM
    @Column(name = "canal_venta")
    private String canalVenta;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    // RETIRO, ENVIO
    @Column(name = "tipo_entrega")
    private String tipoEntrega;

    // PENDIENTE, COMPLETADA, CANCELADA
    @Column(name = "estado")
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "observaciones")
    private String observaciones;
}
