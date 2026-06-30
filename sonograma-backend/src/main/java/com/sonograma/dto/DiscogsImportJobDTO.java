package com.sonograma.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscogsImportJobDTO {
    private Long id;
    private String nombreArchivo;
    private String nombreHoja;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int totalRowsRead;
    private int realRowsRead;
    private int physicalExcelLastRow;
    private int blankRowsIgnored;
    private int validReleaseUrls;
    private int validMasterUrls;
    private int visibleDiscogsTextRows;
    private int directUrlRows;
    private int sellReleaseUrlRows;
    private int embeddedHyperlinkRows;
    private int needsManualMatch;
    private int ignored;
    private int soldRows;
    private int reservedRows;
    private int availableRows;
    private int invalidRows;
    private int metadataFetched;
    private int metadataPending;
    private int failed;
    private int rateLimited;
    private int imported;
    private int coversDownloaded;
    private int coversMissing;
    private int mp3PreviewsFound;
    private int youtubeLinksFound;
    private int qrEntriesCreated;
    private int pending;
    private int readyToImport;
    private List<DiscogsImportRowDTO> rows;
}
