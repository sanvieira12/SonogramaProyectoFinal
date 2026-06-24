package com.sonograma.dto;

import java.util.List;

public record VinylFutureImportSummaryDTO(
    int recordsDetected,
    int recordsImported,
    int coversFound,
    int mp3PreviewsFound,
    int youtubeLinksFound,
    int qrEntriesCreated,
    int failedLinks,
    int skippedDuplicates,
    int rateLimitFailures,
    List<String> failedLinkDetails
) {}
