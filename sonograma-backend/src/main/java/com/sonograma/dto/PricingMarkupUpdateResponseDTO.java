package com.sonograma.dto;

import java.math.BigDecimal;

public record PricingMarkupUpdateResponseDTO(
    Long idDisco,
    BigDecimal markup,
    BigDecimal finalSalePriceUyu,
    String pricingMode
) {}
