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

    @Column(name = "envio")
    private String envio;

    @Column(name = "pago")
    private String pago;

    @Column(name = "moneda")
    @Builder.Default
    private String moneda = "EUR";

    @Column(name = "peso_total_kg", precision = 8, scale = 3)
    private BigDecimal pesoTotalKg;

    @Column(name = "unidad_peso")
    private String unidadPeso;

    @Column(name = "terminos_venta")
    private String terminosVenta;

    @Column(name = "codigo_arancel")
    private String codigoArancel;

    @Column(name = "eori_no")
    private String eoriNo;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "pdf_original_filename")
    private String pdfOriginalFilename;

    @Column(name = "pdf_content_type")
    private String pdfContentType;

    @Column(name = "pdf_storage_path")
    private String pdfStoragePath;

    @Column(name = "pdf_uploaded_at")
    private LocalDateTime pdfUploadedAt;

    @Column(name = "texto_extraido", columnDefinition = "TEXT")
    private String textoExtraido;

    @Column(name = "franqueo", precision = 14, scale = 6)
    private BigDecimal franqueo;

    @Column(name = "tarifas", precision = 14, scale = 6)
    private BigDecimal tarifas;

    @Column(name = "neto", precision = 10, scale = 2)
    private BigDecimal neto;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "iva", precision = 10, scale = 2)
    private BigDecimal iva;

    @Column(name = "cantidad_total_pdf")
    private Integer cantidadTotalPdf;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_status", nullable = false)
    @Builder.Default
    private ImportStatus importStatus = ImportStatus.PARSED;

    @Column(name = "tipo_cambio", precision = 14, scale = 8)
    @Builder.Default
    private BigDecimal tipoCambio = new BigDecimal("50");

    @Column(name = "extra_costo_simple", precision = 14, scale = 6)
    @Builder.Default
    private BigDecimal extraCostoSimple = new BigDecimal("5");

    @Column(name = "extra_costo_doble", precision = 14, scale = 6)
    @Builder.Default
    private BigDecimal extraCostoDoble = new BigDecimal("8");

    @Column(name = "markup_simple", precision = 14, scale = 8)
    @Builder.Default
    private BigDecimal markupSimple = new BigDecimal("1.6");

    @Column(name = "markup_doble", precision = 14, scale = 8)
    @Builder.Default
    private BigDecimal markupDoble = new BigDecimal("1.4");

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PedidoItem> items = new ArrayList<>();
}
