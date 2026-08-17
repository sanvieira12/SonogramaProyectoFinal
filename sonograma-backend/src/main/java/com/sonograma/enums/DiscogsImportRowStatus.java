package com.sonograma.enums;

public enum DiscogsImportRowStatus {
    PENDING,
    PARSED,
    FETCHING_DISCOGS,
    IMPORTED,
    ALREADY_IMPORTED,
    NEEDS_MANUAL_MATCH,
    IGNORED,
    RATE_LIMITED,
    PENDING_RETRY,
    SOLD,
    RESERVED,
    FAILED
}
