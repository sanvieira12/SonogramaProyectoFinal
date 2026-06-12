package com.sonograma.dto;

import java.math.BigDecimal;
import java.util.List;

public record VinylPageData(
    String sourceUrl,
    String artist,
    String title,
    String code,
    String label,
    String genre,
    Integer year,
    String country,
    String format,
    String condition,
    String description,
    BigDecimal purchasePrice,
    String frontImageUrl,
    String backImageUrl,
    List<TrackInfo> tracks
) {}
