package com.sonograma.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CatalogPricingService {

    public static final BigDecimal TIPO_CAMBIO = new BigDecimal("50");
    public static final BigDecimal EXTRA_SIMPLE = new BigDecimal("5");
    public static final BigDecimal EXTRA_DOBLE = new BigDecimal("8");
    public static final BigDecimal MARKUP_SIMPLE = new BigDecimal("1.6");
    public static final BigDecimal MARKUP_DOBLE = new BigDecimal("1.4");

    public PricingResult calcular(BigDecimal purchasePriceEur, String formato) {
        if (purchasePriceEur == null || purchasePriceEur.compareTo(BigDecimal.ZERO) <= 0) return null;

        boolean doble = esDoble(formato);
        BigDecimal extra = doble ? EXTRA_DOBLE : EXTRA_SIMPLE;
        BigDecimal markup = doble ? MARKUP_DOBLE : MARKUP_SIMPLE;
        BigDecimal realCostEur = purchasePriceEur.add(extra);
        BigDecimal realCostUyu = realCostEur.multiply(TIPO_CAMBIO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal salePriceUyu = realCostUyu.multiply(markup).setScale(2, RoundingMode.HALF_UP);
        return new PricingResult(extra, realCostEur, realCostUyu, markup, salePriceUyu);
    }

    public boolean esDoble(String formato) {
        if (formato == null) return false;
        String normalized = formato.strip().toLowerCase();
        return normalized.equals("double") || normalized.startsWith("2x") || normalized.contains("double");
    }

    public record PricingResult(
        BigDecimal extraCostEur,
        BigDecimal realCostEur,
        BigDecimal realCostUyu,
        BigDecimal markup,
        BigDecimal salePriceUyu
    ) {}
}
