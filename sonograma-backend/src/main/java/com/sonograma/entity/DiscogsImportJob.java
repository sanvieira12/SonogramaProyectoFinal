package com.sonograma.entity;

import com.sonograma.enums.DiscogsImportJobStatus;
import com.sonograma.enums.DiscogsImportStage;
import com.sonograma.enums.DiscogsZipStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discogs_import_job")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscogsImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_discogs_import_job")
    private Long idDiscogsImportJob;

    @Column(name = "nombre_archivo", nullable = false, length = 500)
    private String nombreArchivo;

    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;

    @Column(name = "extra_columns", columnDefinition = "TEXT")
    private String extraColumns;

    @Column(name = "nombre_hoja", length = 255)
    private String nombreHoja;

    @Column(name = "physical_excel_last_row")
    private Integer physicalExcelLastRow;

    @Column(name = "ignored_blank_rows", nullable = false)
    @Builder.Default
    private Integer ignoredBlankRows = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DiscogsImportJobStatus status = DiscogsImportJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 50)
    @Builder.Default
    private DiscogsImportStage stage = DiscogsImportStage.PARSING_ROWS;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "zip_status", nullable = false, length = 50)
    @Builder.Default
    private DiscogsZipStatus zipStatus = DiscogsZipStatus.NOT_STARTED;

    @Column(name = "zip_total_covers", nullable = false)
    @Builder.Default
    private Integer zipTotalCovers = 0;

    @Column(name = "zip_processed_covers", nullable = false)
    @Builder.Default
    private Integer zipProcessedCovers = 0;

    @Column(name = "zip_added_covers", nullable = false)
    @Builder.Default
    private Integer zipAddedCovers = 0;

    @Column(name = "zip_failed_covers", nullable = false)
    @Builder.Default
    private Integer zipFailedCovers = 0;

    @Column(name = "zip_current_release", length = 500)
    private String zipCurrentRelease;

    @Column(name = "zip_file_name", length = 255)
    private String zipFileName;

    @Column(name = "zip_error", columnDefinition = "TEXT")
    private String zipError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sourceExcelRowNumber ASC")
    @Builder.Default
    private List<DiscogsImportRow> rows = new ArrayList<>();

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
