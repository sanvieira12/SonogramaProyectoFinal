package com.sonograma.service;

import java.math.BigDecimal;
import java.util.List;

/** Aggregate result shared by sale, period, report, and export consumers. */
public record ProfitResult(
        BigDecimal netProfit,
        ProfitStatus status,
        int affectedItemCount,
        List<ProfitItemResult> items
) {
    public BigDecimal grossProfit() { return netProfit; }
    public boolean grossProfitAvailable() { return status != ProfitStatus.UNAVAILABLE; }
    public ProfitResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isAvailable() {
        return status != ProfitStatus.UNAVAILABLE;
    }
}
