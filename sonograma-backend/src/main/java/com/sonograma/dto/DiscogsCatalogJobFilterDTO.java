package com.sonograma.dto;

import java.time.LocalDateTime;

/** A Discogs Excel job that has catalogue products available for filtering. */
public record DiscogsCatalogJobFilterDTO(
        Long id,
        String nombreArchivo,
        LocalDateTime createdAt,
        long productos
) {}
