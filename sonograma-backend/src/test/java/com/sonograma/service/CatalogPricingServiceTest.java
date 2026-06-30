package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.PricingApplyRequestDTO;
import com.sonograma.dto.PricingPreviewResponseDTO;
import com.sonograma.dto.PricingSettingsUpdateDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.PricingRoundingRule;
import com.sonograma.enums.RecordType;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PedidoRepository;
import com.sonograma.repository.PricingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogPricingServiceTest {

    private PricingSettingsRepository pricingSettingsRepository;
    private DiscoRepository discoRepository;
    private PedidoRepository pedidoRepository;
    private CatalogPricingService service;

    @BeforeEach
    void setUp() {
        pricingSettingsRepository = mock(PricingSettingsRepository.class);
        discoRepository = mock(DiscoRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        service = new CatalogPricingService(pricingSettingsRepository, discoRepository, pedidoRepository);
        when(pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(pricingSettingsRepository.save(any(PricingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.findByNumeroFacturaIn(anySet())).thenReturn(List.of());
    }

    @Test
    void calculatesSingleRecord() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10.00"), 1, "LP");

        assertEquals(RecordType.SINGLE, result.recordType());
        assertEquals(new BigDecimal("10.00"), result.unitLineTotalEur());
        assertEquals(new BigDecimal("5"), result.extraCostEur());
        assertEquals(new BigDecimal("15.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("742.50"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.7"), result.markup());
        assertEquals(new BigDecimal("1260"), result.finalPriceUyu());
    }

    @Test
    void calculatesDoubleRecordFrom2x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10.00"), 1, "2x12\"");

        assertEquals(RecordType.DOUBLE, result.recordType());
        assertEquals(new BigDecimal("8"), result.extraCostEur());
        assertEquals(new BigDecimal("18.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("891.00"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.5"), result.markup());
        assertEquals(new BigDecimal("1340"), result.finalPriceUyu());
    }

    @Test
    void calculatesMultiRecordFrom3x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("12.00"), 1, "3x12\"");

        assertEquals(RecordType.MULTI, result.recordType());
        assertEquals(new BigDecimal("9"), result.extraCostEur());
        assertEquals(new BigDecimal("21.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("1039.50"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.4"), result.markup());
        assertEquals(new BigDecimal("1460"), result.finalPriceUyu());
    }

    @Test
    void keepsManualPriceWhenApplyingAutomaticScope() {
        Disco manual = Disco.builder()
            .idDisco(1L)
            .codigoInterno("MAN-1")
            .artista("Artist")
            .album("Manual")
            .costo(new BigDecimal("10"))
            .formato("LP")
            .precioVenta(new BigDecimal("999"))
            .pricingMode(PricingMode.MANUAL)
            .build();
        when(discoRepository.findAll()).thenReturn(List.of(manual));

        service.apply(new PricingApplyRequestDTO(defaultSettings(), "automatic"));

        assertEquals(new BigDecimal("999"), manual.getPrecioVenta());
        assertEquals(PricingMode.MANUAL, manual.getPricingMode());
    }

    @Test
    void recalculatesWithChangedExchangeRate() {
        PricingSettingsUpdateDTO settings = new PricingSettingsUpdateDTO(
            new BigDecimal("55"),
            new BigDecimal("5"),
            new BigDecimal("8"),
            new BigDecimal("9"),
            new BigDecimal("1.7"),
            new BigDecimal("1.5"),
            new BigDecimal("1.4"),
            PricingRoundingRule.NEAREST_10
        );

        PricingPreviewResponseDTO preview = service.preview(settings);
        assertNotNull(preview.settings());

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10"), 1, "LP", pricingFrom(settings));
        assertEquals(new BigDecimal("825.00"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1400"), result.finalPriceUyu());
    }

    @Test
    void recalculatesWithChangedMarkup() {
        PricingSettingsUpdateDTO settings = new PricingSettingsUpdateDTO(
            new BigDecimal("49.5"),
            new BigDecimal("5"),
            new BigDecimal("8"),
            new BigDecimal("9"),
            new BigDecimal("2.0"),
            new BigDecimal("1.5"),
            new BigDecimal("1.4"),
            PricingRoundingRule.NEAREST_10
        );

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10"), 1, "LP", pricingFrom(settings));
        assertEquals(new BigDecimal("1490"), result.finalPriceUyu());
    }

    @Test
    void appliesRoundingRuleNone() {
        PricingSettingsUpdateDTO settings = new PricingSettingsUpdateDTO(
            new BigDecimal("49.5"),
            new BigDecimal("5"),
            new BigDecimal("8"),
            new BigDecimal("9"),
            new BigDecimal("1.7"),
            new BigDecimal("1.5"),
            new BigDecimal("1.4"),
            PricingRoundingRule.NONE
        );

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10"), 1, "LP", pricingFrom(settings));
        assertEquals(new BigDecimal("1262.25"), result.finalPriceUyu());
    }

    @Test
    void marksManualOverrideWhenPriceEdited() {
        Disco disco = Disco.builder()
            .precioVenta(new BigDecimal("1260"))
            .pricingMode(PricingMode.AUTO)
            .build();
        DiscoRequestDTO request = new DiscoRequestDTO();
        request.setPrecioVenta(new BigDecimal("1500"));

        assertTrue(service.isManualOverride(disco, request));
    }

    private PricingSettingsUpdateDTO defaultSettings() {
        return new PricingSettingsUpdateDTO(
            new BigDecimal("49.5"),
            new BigDecimal("5"),
            new BigDecimal("8"),
            new BigDecimal("9"),
            new BigDecimal("1.7"),
            new BigDecimal("1.5"),
            new BigDecimal("1.4"),
            PricingRoundingRule.NEAREST_10
        );
    }

    private PricingSettings pricingFrom(PricingSettingsUpdateDTO settings) {
        PricingSettings pricing = new PricingSettings();
        pricing.setId(1L);
        pricing.setEurUyuRate(settings.eurUyuRate());
        pricing.setExtraCostSingleEur(settings.extraCostSingleEur());
        pricing.setExtraCostDoubleEur(settings.extraCostDoubleEur());
        pricing.setExtraCostMultiEur(settings.extraCostMultiEur());
        pricing.setMarkupSingle(settings.markupSingle());
        pricing.setMarkupDouble(settings.markupDouble());
        pricing.setMarkupMulti(settings.markupMulti());
        pricing.setRoundingRule(settings.roundingRule());
        return pricing;
    }
}
