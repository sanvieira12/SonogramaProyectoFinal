package com.sonograma.service;

import java.math.BigDecimal;
import java.util.List;

/** Read-only/auditable result of historical profit backfill analysis. */
public record ProfitBackfillReport(
        boolean executed,
        int totalSaleDetails,
        int recalculableReliably,
        int requiringFallback,
        int unavailable,
        int updated,
        List<Sample> samples
) {
    public record Sample(Long detailId, BigDecimal beforeGrossProfit, BigDecimal afterGrossProfit,
                         String costSource, String status) {}
}
