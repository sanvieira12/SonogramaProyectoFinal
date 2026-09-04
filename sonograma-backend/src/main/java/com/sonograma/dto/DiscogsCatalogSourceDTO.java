package com.sonograma.dto;

import com.sonograma.enums.DiscogsManualBatchStatus;

import java.time.LocalDateTime;

/** A client-facing logical source that may comprise one or more Discogs jobs. */
public record DiscogsCatalogSourceDTO(
        String key,
        String type,
        String label,
        long productos,
        String customerCode,
        DiscogsManualBatchStatus status,
        Long batchId,
        LocalDateTime createdAt
) {
    /** Compatibility constructor for historical Excel source projections. */
    public DiscogsCatalogSourceDTO(String key, String label, long productos) {
        this(keyWithExcelPrefix(key), "EXCEL", label, productos, null, null, null, null);
    }

    /** Historical Excel projection with its newest job timestamp. */
    public DiscogsCatalogSourceDTO(String key, String label, long productos, LocalDateTime createdAt) {
        this(keyWithExcelPrefix(key), "EXCEL", label, productos, null, null, null, createdAt);
    }

    /** Manual batch projection; the stable key is derived from its persistent id. */
    public DiscogsCatalogSourceDTO(
            String customerCode,
            long copyCount,
            DiscogsManualBatchStatus status,
            Long batchId,
            LocalDateTime createdAt
    ) {
        this("manual:" + batchId, "MANUAL", null, copyCount, customerCode, status, batchId, createdAt);
    }

    /** Alias for clients that prefer the domain name over the legacy productos field. */
    public long getCopyCount() {
        return productos;
    }

    private static String keyWithExcelPrefix(String key) {
        return key != null && key.regionMatches(true, 0, "excel:", 0, "excel:".length())
                ? key : "excel:" + key;
    }
}
