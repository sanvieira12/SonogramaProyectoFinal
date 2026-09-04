package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoImportPreviewDTO {
    private String artista;
    private String album;
    private String sello;
    private Integer anio;
    private String pais;
    private String genero;
    private String estilo;
    private String formato;
    private String condicion;
    private String codigoInterno;
    private String imagenUrl;
    private String previewUrl;
    private String discogsUrl;
    /** Concrete resolved Discogs release id; never a master id. */
    private Long discogsReleaseId;
    /** Server-persisted identity of this one manual confirmation. */
    private String operationId;
    private Boolean productoExistente;
    private Integer copiasDisponibles;
    private String tracklist;
    private List<TrackInfo> tracks;
    private BigDecimal precioVenta;
    /** Copy-level price for the persistent manual customer batch workflow. */
    private BigDecimal copySalePrice;
    /** Customer code for the persistent manual customer batch workflow. */
    private String customerCode;
    /** Free-text physical grade for the exact copy received. */
    private String physicalCondition;
    private BigDecimal costo;
    private Integer cantidadCopias;
    private String estado;
    private String procedencia;
    private String notas;

    @Builder.Default
    private List<String> errores = new ArrayList<>();

    private Integer filaExcel;
}
