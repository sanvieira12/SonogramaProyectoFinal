package com.sonograma.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the calc chain (replicating JUNIO 1.xlsx):
 *   extraCostEUR  = type-specific extra cost
 *   realCostEUR   = unitPrice + extraCostEUR
 *   realCostUYU   = realCostEUR * exchangeRate
 *   markup        = type-specific multiplier
 *   finalPriceUYU = realCostUYU * markup
 */
class PedidoServiceCalcTest {

    // Excel reference values (JUNIO 1.xlsx):
    // unitPrice=12,50 EUR; extraSingle=2,00 EUR; tipoCambio=43,50; markupSingle=2,50
    // → realCostEUR = 14,50 ; realCostUYU = 630,75 ; finalPriceUYU = 1576,88

    private static final BigDecimal UNIT_PRICE    = new BigDecimal("12.50");
    private static final BigDecimal EXTRA_SINGLE  = new BigDecimal("2.00");
    private static final BigDecimal EXTRA_DOUBLE  = new BigDecimal("4.00");
    private static final BigDecimal TIPO_CAMBIO   = new BigDecimal("43.50");
    private static final BigDecimal MARKUP_SINGLE = new BigDecimal("2.50");
    private static final BigDecimal MARKUP_DOUBLE = new BigDecimal("2.20");

    @Test
    void singleCalcMatchesExcel() {
        BigDecimal costoRealEur = UNIT_PRICE.add(EXTRA_SINGLE);
        assertEquals(new BigDecimal("14.50"), costoRealEur);

        BigDecimal costoRealUyu = costoRealEur.multiply(TIPO_CAMBIO).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("630.75"), costoRealUyu);

        BigDecimal precioFinal = costoRealUyu.multiply(MARKUP_SINGLE).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1576.88"), precioFinal);
    }

    @Test
    void doubleCalcUsesDoubleMarkupAndExtraCost() {
        BigDecimal costoRealEur = UNIT_PRICE.add(EXTRA_DOUBLE);
        assertEquals(new BigDecimal("16.50"), costoRealEur);

        BigDecimal costoRealUyu = costoRealEur.multiply(TIPO_CAMBIO).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("717.75"), costoRealUyu);

        BigDecimal precioFinal = costoRealUyu.multiply(MARKUP_DOUBLE).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1579.05"), precioFinal);
    }

    @Test
    void zeroExtraCostLeavesRealCostEqualToUnitPrice() {
        BigDecimal costoRealEur = UNIT_PRICE.add(BigDecimal.ZERO);
        assertEquals(UNIT_PRICE, costoRealEur);
    }
}
