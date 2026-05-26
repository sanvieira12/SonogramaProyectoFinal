package com.sonograma.entity;

import com.sonograma.enums.EstadoShippingOrder;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipping_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_shipping_order")
    private Long idShippingOrder;

    @Column(name = "numero", unique = true)
    private String numero;

    @Column(name = "proveedor")
    @Builder.Default
    private String proveedor = "Vinyl Future";

    @Column(name = "fecha_orden")
    private LocalDate fechaOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Builder.Default
    private EstadoShippingOrder estado = EstadoShippingOrder.PENDIENTE;

    @Column(name = "costo_total", precision = 10, scale = 2)
    private BigDecimal costoTotal;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_creacion")
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @OneToMany(mappedBy = "shippingOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShippingOrderItem> items = new ArrayList<>();
}
