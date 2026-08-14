package com.sonograma.service.crm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sonograma.crm.recommendation")
public class CrmRecommendationProperties {
    private BigDecimal historicalWeight = new BigDecimal("0.40");
    private BigDecimal recentWeight = new BigDecimal("0.60");
    private BigDecimal manualInterestWeight = new BigDecimal("30");
    private BigDecimal artistWeight = new BigDecimal("25");
    private BigDecimal labelWeight = new BigDecimal("15");
    private BigDecimal genreWeight = new BigDecimal("12");
    private BigDecimal styleWeight = new BigDecimal("12");
    private BigDecimal periodWeight = new BigDecimal("10");
    private BigDecimal priceWeight = new BigDecimal("8");
    private BigDecimal formatWeight = new BigDecimal("5");
    private BigDecimal conditionWeight = new BigDecimal("3");
    private BigDecimal highAffinityThreshold = new BigDecimal("50");
    private BigDecimal mediumAffinityThreshold = new BigDecimal("25");
    private int defaultLimit = 20;
    private int maximumLimit = 100;

    public BigDecimal maximumRawScore() {
        return manualInterestWeight.add(artistWeight).add(labelWeight).add(genreWeight)
                .add(styleWeight).add(periodWeight).add(priceWeight).add(formatWeight).add(conditionWeight);
    }
}
