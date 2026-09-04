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
    private String stage;
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
    private int alreadyImported;
    private int coversDownloaded;
    private int coversMissing;
    private int mp3PreviewsFound;
    private int youtubeLinksFound;
    private int qrEntriesCreated;
    private int pending;
    private int readyToImport;
    private int linksDetected;
    private int missingDiscogsLinks;
    private int warnings;
    private int rowsDetected;
    private int rowsImported;
    private int catalogProductsAffected;
    private int newProducts;
    private int existingProducts;
    private int physicalCopiesImported;
    private int physicalCopiesToReceive;
    private int resolvedConcreteReleases;
    private int pendingRows;
    private int errorRows;
    private int rowsRequiringReview;
    private int rowsWithFullMetadata;
    private int rowsWithWarnings;
    private int rowsTechnicallyImpossible;
    private int metadataFailed;
    private int coversPending;
    private int youtubeTracksMissing;
    private int meaningfulRows;
    private int identityBearingRows;
    private int newCopiesToReceive;
    private int alreadyReceivedRows;
    private int availableCopiesToReceive;
    private int soldCopiesToReceive;
    private int noPriceRows;
    private int noPriceReceivableRows;
    private int manualReviewRows;
    private int unresolvedRows;
    private int metadataErrorRows;
    private int blockedIdentityRows;
    private int readyRows;
    private boolean canConfirm;
    private String zipStatus;
    private int zipTotalCovers;
    private int zipProcessedCovers;
    private int zipAddedCovers;
    private int zipFailedCovers;
    private int zipProgressPercentage;
    private String zipCurrentRelease;
    private boolean zipReady;
    private String zipError;
    private List<String> extraColumns;
    private List<DiscogsImportRowDTO> rows;
}
