package com.sonograma.dto;

public record AudioPreviewRequestDTO(
    String trackName,
    String trackPosition,
    String audioUrl,
    String youtubeUrl,
    Integer durationSeconds
) {}
