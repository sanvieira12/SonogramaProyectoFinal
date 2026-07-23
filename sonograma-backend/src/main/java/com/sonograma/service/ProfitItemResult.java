package com.sonograma.service;

import java.math.BigDecimal;

/**
 * Profit calculation for one sold line. The acquisition cost is a line total,
 * not a unit amount, so quantity is never multiplied twice by consumers.
 */
public record ProfitItemResult(
        Long detailId,
        Long discoId,
        int quantity,
        BigDecimal actualSaleAmount,
        BigDecimal acquisitionCost,
        BigDecimal netProfit,
        ProfitStatus status,
        String unavailableReason,
        String costSource,
        String originalCostCurrency,
        BigDecimal exchangeRateUsed,
        boolean costComplete
) {
    public BigDecimal grossProfit() { return netProfit; }
    public boolean isAvailable() {
        return status != ProfitStatus.UNAVAILABLE;
    }
}
