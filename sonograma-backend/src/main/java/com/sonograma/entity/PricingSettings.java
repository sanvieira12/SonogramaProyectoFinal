package com.sonograma.entity;

import com.sonograma.enums.PricingRoundingRule;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "eur_uyu_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal eurUyuRate;

    @Column(name = "extra_cost_single_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraCostSingleEur;

    @Column(name = "extra_cost_double_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraCostDoubleEur;

    @Column(name = "extra_cost_multi_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraCostMultiEur;

    @Column(name = "markup_single", nullable = false, precision = 10, scale = 4)
    private BigDecimal markupSingle;

    @Column(name = "markup_double", nullable = false, precision = 10, scale = 4)
    private BigDecimal markupDouble;

    @Column(name = "markup_multi", nullable = false, precision = 10, scale = 4)
    private BigDecimal markupMulti;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_rule", nullable = false, length = 32)
    private PricingRoundingRule roundingRule;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
