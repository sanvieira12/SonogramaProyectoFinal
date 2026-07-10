package com.sonograma.repository;

import com.sonograma.entity.Disco;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.PricingRoundingRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class PricingPrecisionPersistenceTest {

    @Autowired
    private DiscoRepository discoRepository;

    @Autowired
    private PricingSettingsRepository pricingSettingsRepository;

    @Test
    void persistsAndReloadsDiscPricingDecimals() {
        Disco saved = discoRepository.save(Disco.builder()
            .codigoInterno("DISC-PRECISION")
            .artista("Artista")
            .album("Album")
            .costo(new BigDecimal("13.3644"))
            .cantidadCopias(1)
            .formato("LP")
            .precioVenta(new BigDecimal("1396.662489"))
            .pricingMode(PricingMode.MANUAL)
            .manualMarkup(new BigDecimal("1.53098765"))
            .build());

        Disco reloaded = discoRepository.findById(saved.getIdDisco()).orElseThrow();

        assertEquals(0, new BigDecimal("13.3644").compareTo(reloaded.getCosto()));
        assertEquals(0, new BigDecimal("1396.662489").compareTo(reloaded.getPrecioVenta()));
        assertEquals(0, new BigDecimal("1.53098765").compareTo(reloaded.getManualMarkup()));
    }

    @Test
    void persistsAndReloadsSettingsDecimals() {
        pricingSettingsRepository.save(PricingSettings.builder()
            .id(PricingSettings.SINGLETON_ID)
            .eurUyuRate(new BigDecimal("49.37000000"))
            .extraCostSingleEur(new BigDecimal("5.1256"))
            .extraCostDoubleEur(new BigDecimal("8.3333"))
            .extraCostMultiEur(new BigDecimal("9.4444"))
            .markupSingle(new BigDecimal("1.53098765"))
            .markupDouble(new BigDecimal("1.45670000"))
            .markupMulti(new BigDecimal("1.40120000"))
            .roundingRule(PricingRoundingRule.NONE)
            .build());

        PricingSettings reloaded = pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID).orElseThrow();

        assertEquals(0, new BigDecimal("49.37").compareTo(reloaded.getEurUyuRate()));
        assertEquals(0, new BigDecimal("5.1256").compareTo(reloaded.getExtraCostSingleEur()));
        assertEquals(0, new BigDecimal("1.53098765").compareTo(reloaded.getMarkupSingle()));
    }
}
