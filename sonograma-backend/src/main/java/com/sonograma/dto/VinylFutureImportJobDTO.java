package com.sonograma.dto;

import com.sonograma.enums.VinylFutureImportJobStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VinylFutureImportJobDTO(
    String jobId,
    String source,
    VinylFutureImportJobStatus status,
    LocalDateTime createdAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    int progressPercent,
    String currentStep,
    String invoiceNumber,
    LocalDate invoiceDate,
    Integer totalItems,
    Integer totalQuantity,
    int processedItems,
    int successCount,
    int failedCount,
    int skippedCount,
    String importId,
    VinylFutureImportSummaryDTO summary,
    List<String> warnings,
    List<String> errors,
    List<VinylFutureImportJobItemDTO> items
) {}
