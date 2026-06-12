package com.sonograma.entity;

import com.sonograma.enums.ImportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long idPedido;

    @Column(name = "numero_factura")
    private String numeroFactura;

    @Column(name = "fecha_factura")
    private LocalDate fechaFactura;

    @Column(name = "proveedor")
    private String proveedor;

    @Column(name = "pago")
    private String pago;

    @Column(name = "moneda")
    @Builder.Default
    private String moneda = "EUR";

    @Column(name = "peso_total_kg", precision = 8, scale = 3)
    private BigDecimal pesoTotalKg;

    @Column(name = "terminos_venta")
    private String terminosVenta;

    @Column(name = "codigo_arancel")
    private String codigoArancel;

    @Column(name = "eori_no")
    private String eoriNo;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String textoExtraido;

    @Column(name = "franqueo", precision = 10, scale = 2)
    private BigDecimal franqueo;

    @Column(name = "tarifas", precision = 10, scale = 2)
    private BigDecimal tarifas;

    @Column(name = "neto", precision = 10, scale = 2)
    private BigDecimal neto;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "cantidad_total_pdf")
    private Integer cantidadTotalPdf;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_status", nullable = false)
    @Builder.Default
    private ImportStatus importStatus = ImportStatus.PARSED;

    @Column(name = "tipo_cambio", precision = 10, scale = 4)
    private BigDecimal tipoCambio;

    @Column(name = "extra_costo_simple", precision = 10, scale = 2)
    private BigDecimal extraCostoSimple;

    @Column(name = "extra_costo_doble", precision = 10, scale = 2)
    private BigDecimal extraCostoDoble;

    @Column(name = "markup_simple", precision = 8, scale = 4)
    private BigDecimal markupSimple;

    @Column(name = "markup_doble", precision = 8, scale = 4)
    private BigDecimal markupDoble;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PedidoItem> items = new ArrayList<>();
}
