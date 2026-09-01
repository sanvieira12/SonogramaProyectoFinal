package com.sonograma.dto;

import java.util.List;

public record VinylFutureImportSummaryDTO(
    String importId,
    int recordsDetected,
    int recordsImported,
    int coversFound,
    int coversDownloaded,
    int mp3PreviewsFound,
    int mp3Downloaded,
    int youtubeLinksFound,
    int qrEntriesCreated,
    int failedMediaDownloads,
    int failedLinks,
    int skippedDuplicates,
    int rateLimitFailures,
    List<String> failedLinkDetails,
    String invoiceNumber,
    Integer declaredCopies,
    int importedCopies,
    int pendingCopies,
    int pendingSourceRows,
    boolean partialImport,
    String zipStatus
) {
    public VinylFutureImportSummaryDTO(
            String importId, int recordsDetected, int recordsImported, int coversFound,
            int coversDownloaded, int mp3PreviewsFound, int mp3Downloaded,
            int youtubeLinksFound, int qrEntriesCreated, int failedMediaDownloads,
            int failedLinks, int skippedDuplicates, int rateLimitFailures,
            List<String> failedLinkDetails) {
        this(importId, recordsDetected, recordsImported, coversFound, coversDownloaded,
            mp3PreviewsFound, mp3Downloaded, youtubeLinksFound, qrEntriesCreated,
            failedMediaDownloads, failedLinks, skippedDuplicates, rateLimitFailures,
            failedLinkDetails, null, null, 0, 0, 0, false, "PENDIENTE");
    }

    public VinylFutureImportSummaryDTO withImportId(String value) {
        return new VinylFutureImportSummaryDTO(
            value,
            recordsDetected,
            recordsImported,
            coversFound,
            coversDownloaded,
            mp3PreviewsFound,
            mp3Downloaded,
            youtubeLinksFound,
            qrEntriesCreated,
            failedMediaDownloads,
            failedLinks,
            skippedDuplicates,
            rateLimitFailures,
            failedLinkDetails,
            invoiceNumber,
            declaredCopies,
            importedCopies,
            pendingCopies,
            pendingSourceRows,
            partialImport,
            zipStatus
        );
    }

    public VinylFutureImportSummaryDTO withZipStatus(String value) {
        return new VinylFutureImportSummaryDTO(
            importId, recordsDetected, recordsImported, coversFound, coversDownloaded,
            mp3PreviewsFound, mp3Downloaded, youtubeLinksFound, qrEntriesCreated,
            failedMediaDownloads, failedLinks, skippedDuplicates, rateLimitFailures,
            failedLinkDetails, invoiceNumber, declaredCopies, importedCopies,
            pendingCopies, pendingSourceRows, partialImport, value
        );
    }
}
