package com.sonograma.entity;

import com.sonograma.enums.EnrichStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pedido_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido_item")
    private Long idPedidoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "artista")
    private String artista;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "formato")
    private String formato;

    @Column(name = "precio_unitario_eur", precision = 10, scale = 2)
    private BigDecimal precioUnitarioEur;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "total_linea_eur", precision = 10, scale = 2)
    private BigDecimal totalLineaEur;

    @Column(name = "extra_costo_eur", precision = 10, scale = 2)
    private BigDecimal extraCostoEur;

    @Column(name = "costo_real_eur", precision = 10, scale = 2)
    private BigDecimal costoRealEur;

    @Column(name = "costo_real_uyu", precision = 10, scale = 2)
    private BigDecimal costoRealUyu;

    @Column(name = "markup", precision = 8, scale = 4)
    private BigDecimal markup;

    @Column(name = "precio_final_uyu", precision = 10, scale = 2)
    private BigDecimal precioFinalUyu;

    @Column(name = "portada_url")
    private String portadaUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_disco")
    private Disco disco;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrich_status")
    @Builder.Default
    private EnrichStatus enrichStatus = EnrichStatus.PENDING;

    // JSON array of scraped tracks, staged here until catalog import creates CatalogAudioPreview rows
    @Column(name = "tracks_json", columnDefinition = "TEXT")
    private String tracksJson;

    // Full scraped page metadata, kept until the item is imported into catalog.
    @Column(name = "page_data_json", columnDefinition = "TEXT")
    private String pageDataJson;
}
