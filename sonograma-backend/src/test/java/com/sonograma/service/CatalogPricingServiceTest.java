package com.sonograma.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogPricingServiceTest {

    private final CatalogPricingService service = new CatalogPricingService();

    @Test
    void calculatesSingleUsingCurrentCatalogRules() {
        CatalogPricingService.PricingResult result = service.calcular(new BigDecimal("10.00"), "LP");

        assertEquals(new BigDecimal("15.00"), result.realCostEur());
        assertEquals(new BigDecimal("735.00"), result.realCostUyu());
        assertEquals(new BigDecimal("1176.00"), result.salePriceUyu());
    }

    @Test
    void calculatesDoubleUsingDoubleExtraAndMarkup() {
        CatalogPricingService.PricingResult result = service.calcular(new BigDecimal("10.00"), "2x12");

        assertEquals(new BigDecimal("18.00"), result.realCostEur());
        assertEquals(new BigDecimal("882.00"), result.realCostUyu());
        assertEquals(new BigDecimal("1234.80"), result.salePriceUyu());
    }
}
