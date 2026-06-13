package com.sonograma.dto;

import java.time.LocalDateTime;

public record AudioPreviewDTO(
    Long id,
    Long idDisco,
    String trackName,
    String trackPosition,
    String audioUrl,
    String youtubeUrl,
    Integer durationSeconds,
    String source,
    String status,
    LocalDateTime createdAt
) {}
