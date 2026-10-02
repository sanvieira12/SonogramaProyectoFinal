package com.sonograma.dto;

import java.math.BigDecimal;

/** Values supplied by the operator while a manual receipt is still pending. */
public record ManualDiscogsOperationContextDTO(
        String sourceCustomerCode,
        BigDecimal submittedPrice,
        String submittedCondition
) {}
