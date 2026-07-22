package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "detalle_venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "id_disco")
    private Disco disco;

    @Column(name = "precio_unitario", precision = 10, scale = 2, nullable = false)
    private BigDecimal precioUnitario;

    /**
     * Historical acquisition cost captured when this item is sold.
     * Nullable on purpose: old/manual records may not have trustworthy cost data.
     */
    @Column(name = "costo_adquisicion_unitario", precision = 14, scale = 6)
    private BigDecimal costoAdquisicionUnitario;

    /** Normalized historical unit acquisition cost used by profit calculations. */
    @Column(name = "costo_adquisicion_unitario_uyu", precision = 14, scale = 6)
    private BigDecimal costoAdquisicionUnitarioUyu;

    @Column(name = "costo_adquisicion_moneda_original", length = 10)
    private String costoAdquisicionMonedaOriginal;

    @Column(name = "tipo_cambio_adquisicion", precision = 14, scale = 8)
    private BigDecimal tipoCambioAdquisicion;

    @Column(name = "costo_adquisicion_fuente", length = 80)
    private String costoAdquisicionFuente;

    @Column(name = "cantidad", nullable = false)
    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "artista_snap")
    private String artistaSnap;

    @Column(name = "album_snap")
    private String albumSnap;

    @Column(name = "descripcion_snap")
    private String descripcionSnap;

    @Column(name = "codigo_snap")
    private String codigoSnap;

    @Column(name = "manual_item", nullable = false)
    @Builder.Default
    private Boolean manualItem = false;

    @Column(name = "copy_ids_snapshot", columnDefinition = "TEXT")
    private String copyIdsSnapshot;
}
