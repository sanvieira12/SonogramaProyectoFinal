package com.sonograma.dto;

/** A client-facing logical source that may comprise one or more Discogs jobs. */
public record DiscogsCatalogSourceDTO(
        String key,
        String label,
        long productos
) {}
