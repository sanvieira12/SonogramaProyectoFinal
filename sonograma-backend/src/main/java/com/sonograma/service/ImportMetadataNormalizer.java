package com.sonograma.service;

import java.util.Locale;

public final class ImportMetadataNormalizer {

    public static final String SOURCE_FUTURE = "Future";
    public static final String SOURCE_DISCOGS = "Discogs";
    public static final String SHIPPING_UPS = "UPS";

    private ImportMetadataNormalizer() {
    }

    public static String normalizeSource(String rawValue) {
        String trimmed = blankToNull(rawValue);
        if (trimmed == null) {
            return null;
        }

        String compact = trimmed
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "");

        if (compact.contains("DISCOGS")) {
            return SOURCE_DISCOGS;
        }
        if (compact.contains("VINYLFUTURE") || compact.contains("FUTURE") || compact.contains("DEEJAYDE")) {
            return SOURCE_FUTURE;
        }
        return trimmed;
    }

    public static boolean isFutureSource(String rawValue) {
        return SOURCE_FUTURE.equals(normalizeSource(rawValue));
    }

    public static boolean isDiscogsSource(String rawValue) {
        return SOURCE_DISCOGS.equals(normalizeSource(rawValue));
    }

    public static String normalizeShipping(String source, String rawShipping) {
        if (isFutureSource(source)) {
            return SHIPPING_UPS;
        }
        return blankToNull(rawShipping);
    }

    public static String resolveDisplaySupplier(String recordSource, String fallbackSupplier) {
        String normalizedRecordSource = normalizeSource(recordSource);
        if (normalizedRecordSource != null) {
            return normalizedRecordSource;
        }
        return normalizeSource(fallbackSupplier);
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
