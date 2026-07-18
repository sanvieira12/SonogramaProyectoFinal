package com.sonograma.entity;

import com.sonograma.enums.DiscogsImportRowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "discogs_import_row")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscogsImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_discogs_import_row")
    private Long idDiscogsImportRow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_discogs_import_job", nullable = false)
    private DiscogsImportJob job;

    @Column(name = "source_excel_row_number", nullable = false)
    private Integer sourceExcelRowNumber;

    @Column(name = "visible_cell_value", columnDefinition = "TEXT")
    private String visibleCellValue;

    @Column(name = "hyperlink_url", columnDefinition = "TEXT")
    private String hyperlinkUrl;

    @Column(name = "normalized_discogs_url", columnDefinition = "TEXT")
    private String normalizedDiscogsUrl;

    @Column(name = "url_source", length = 20)
    private String urlSource;

    @Column(name = "discogs_type", length = 20)
    private String discogsType;

    @Column(name = "discogs_id")
    private Long discogsId;

    @Column(name = "master_id")
    private Long masterId;

    @Column(name = "resolved_release_id")
    private Long resolvedReleaseId;

    @Column(name = "artist")
    private String artist;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "raw_condition", length = 255)
    private String rawCondition;

    @Column(name = "manual_condition", length = 255)
    private String manualCondition;

    @Column(name = "raw_price", length = 255)
    private String rawPrice;

    @Column(name = "manual_price_uyu", precision = 10, scale = 2)
    private BigDecimal manualPriceUyu;

    @Column(name = "manual_genre", length = 255)
    private String manualGenre;

    @Column(name = "observation", columnDefinition = "TEXT")
    private String observation;

    @Column(name = "source_status", length = 50)
    private String sourceStatus;

    @Column(name = "internal_code", length = 255)
    private String internalCode;

    @Column(name = "release_year")
    private Integer year;

    @Column(name = "genre")
    private String genre;

    @Column(name = "label")
    private String label;

    @Column(name = "catalog_number")
    private String catalogNumber;

    @Column(name = "country")
    private String country;

    @Column(name = "style")
    private String style;

    @Column(name = "format")
    private String format;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "preview_url", columnDefinition = "TEXT")
    private String previewUrl;

    @Column(name = "tracklist", columnDefinition = "TEXT")
    private String tracklist;

    @Column(name = "tracks_json", columnDefinition = "TEXT")
    private String tracksJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DiscogsImportRowStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_catalog_product_id")
    private Disco importedCatalogProduct;
}
