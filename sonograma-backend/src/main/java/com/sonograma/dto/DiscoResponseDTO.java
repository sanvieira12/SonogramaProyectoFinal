package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoResponseDTO {
    private Long idDisco;
    private String codigoInterno;
    private String codigoQr;
    private String artista;
    private String album;
    private String genero;
    private String selloDiscografico;
    private String descripcion;
    private Integer anio;
    private String condicion;
    private String tipoDisco;
    private String formato;
    private BigDecimal costo;
    private String costoMoneda;
    private String numeroFacturaCompra;
    private LocalDate fechaFacturaCompra;
    private BigDecimal precioVenta;
    private String pricingMode;
    private String recordType;
    private BigDecimal unitLineTotalEur;
    private BigDecimal extraCostEur;
    private BigDecimal realUnitCostEur;
    private BigDecimal realUnitCostUyu;
    private BigDecimal pricingMarkup;
    private String estado;
    private String pais;
    private String estilo;
    private String tracklist;
    private String notas;
    private String procedencia;
    private String imagenUrl;
    private String previewUrl;
    private String discogsUrl;
    private Integer cantidadCopias;
    private Integer totalCopias;
    private Integer copiasVendidas;
    private List<AudioPreviewDTO> audioPreviews;
    private List<DiscoQrCopyDTO> qrCopies;
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaActualizacion;
}
