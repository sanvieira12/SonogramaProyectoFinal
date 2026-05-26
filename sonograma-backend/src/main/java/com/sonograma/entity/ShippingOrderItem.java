package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "shipping_order_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_shipping_order_item")
    private Long idShippingOrderItem;

    @ManyToOne
    @JoinColumn(name = "id_shipping_order", nullable = false)
    private ShippingOrder shippingOrder;

    @ManyToOne
    @JoinColumn(name = "id_disco")
    private Disco disco;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "artista")
    private String artista;

    @Column(name = "album")
    private String album;

    @Column(name = "cantidad")
    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "precio_unitario", precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;
}
