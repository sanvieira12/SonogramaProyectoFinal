package com.sonograma.dto;

import java.math.BigDecimal;
import java.util.List;

public record VinylFutureManualPreviewDTO(
    String previewId,
    Long pendingItemId,
    Integer suggestedQuantity,
    String sourceUrl,
    String catalogueCode,
    String artist,
    String title,
    String format,
    String label,
    Integer year,
    String genre,
    String country,
    String condition,
    String description,
    BigDecimal purchasePrice,
    String coverUrl,
    List<TrackInfo> tracks,
    String metadataStatus,
    boolean existingProduct,
    Long existingProductId,
    boolean coverAvailable
) {}
