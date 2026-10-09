package com.sonograma.dto;

import java.math.BigDecimal;

public record StockValuationDTO(
    BigDecimal importedNewEur,
    BigDecimal importedNewUyu,
    BigDecimal projectedNewUyu,
    BigDecimal projectedUsedKnownUyu,
    long availableNewCopies,
    long availableUsedCopies,
    long usedAvailableCopiesWithoutPrice,
    long newAvailableCopiesWithoutSalePrice,
    long newAvailableCopiesWithoutAcquisitionCost,
    long newAvailableCopiesWithUnknownAcquisitionCurrency
) {}
