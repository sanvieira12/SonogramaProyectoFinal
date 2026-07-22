package com.sonograma.service;

import java.math.BigDecimal;

/** Resolved historical unit acquisition cost, normalized to UYU. */
public record AcquisitionCostResolution(
        BigDecimal unitCostUyu,
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal exchangeRateUsed,
        String source
) {
    public boolean isComplete() {
        return unitCostUyu != null;
    }
}
