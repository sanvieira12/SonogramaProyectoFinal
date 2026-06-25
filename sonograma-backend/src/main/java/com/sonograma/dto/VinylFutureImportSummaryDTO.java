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
    List<String> failedLinkDetails
) {
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
            failedLinkDetails
        );
    }
}
