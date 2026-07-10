package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.PricingApplyRequestDTO;
import com.sonograma.dto.PricingApplyResponseDTO;
import com.sonograma.dto.PricingMarkupUpdateRequestDTO;
import com.sonograma.dto.PricingMarkupUpdateResponseDTO;
import com.sonograma.dto.PricingPreviewResponseDTO;
import com.sonograma.dto.PricingSettingsUpdateDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.PricingRoundingRule;
import com.sonograma.enums.RecordType;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PedidoRepository;
import com.sonograma.repository.PricingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.findByNumeroFacturaIn(anySet())).thenReturn(List.of());
    }

    @Test
    void calculatesSingleRecord() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10.00"), 1, "LP");

        assertEquals(RecordType.SINGLE, result.recordType());
        assertEquals(new BigDecimal("10.00"), result.unitLineTotalEur());
        assertEquals(0, new BigDecimal("5.00").compareTo(result.extraCostEur()));
        assertEquals(new BigDecimal("15.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("742.50"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.7"), result.markup());
        assertEquals(new BigDecimal("1260"), result.finalPriceUyu());
    }

    @Test
    void calculatesDoubleRecordFrom2x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10.00"), 1, "2x12\"");

        assertEquals(RecordType.DOUBLE, result.recordType());
        assertEquals(0, new BigDecimal("8.00").compareTo(result.extraCostEur()));
        assertEquals(new BigDecimal("18.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("891.00"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.5"), result.markup());
        assertEquals(new BigDecimal("1340"), result.finalPriceUyu());
    }

    @Test
    void calculatesMultiRecordFrom3x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("12.00"), 1, "3x12\"");

        assertEquals(RecordType.MULTI, result.recordType());
        assertEquals(0, new BigDecimal("9.00").compareTo(result.extraCostEur()));
        assertEquals(new BigDecimal("21.00"), result.realUnitCostEur());
        assertEquals(new BigDecimal("1039.50"), result.realUnitCostUyu());
        assertEquals(new BigDecimal("1.4"), result.markup());
        assertEquals(new BigDecimal("1460"), result.finalPriceUyu());
    }

    @Test
    void keepsManualPriceWhenApplyingAutomaticScope() {
        Disco manual = disco(1L, "Manual", new BigDecimal("999"), PricingMode.MANUAL, new BigDecimal("2.1000"));
        when(discoRepository.findAll()).thenReturn(List.of(manual));

        service.apply(new PricingApplyRequestDTO(defaultSettings(), "automatic", null));

        assertEquals(new BigDecimal("999"), manual.getPrecioVenta());
        assertEquals(PricingMode.MANUAL, manual.getPricingMode());
        assertEquals(new BigDecimal("2.1000"), manual.getManualMarkup());
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
    void keepsDefaultRoundingRuleEvenIfRequestChangesIt() {
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
        assertEquals(new BigDecimal("1260"), result.finalPriceUyu());
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

    @Test
    void appliesPricingOnlyToSelectedIds() {
        Disco selected = disco(1L, "Selected", new BigDecimal("800"), PricingMode.MANUAL, new BigDecimal("1.9000"));
        Disco unselected = disco(2L, "Unselected", new BigDecimal("950"), PricingMode.MANUAL, new BigDecimal("2.3000"));
        when(discoRepository.findAllById(List.of(1L))).thenReturn(List.of(selected));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "selected", List.of(1L)));

        assertEquals(1, response.updatedCount());
        assertEquals(PricingMode.AUTO, selected.getPricingMode());
        assertNull(selected.getManualMarkup());
        assertEquals(new BigDecimal("1260"), selected.getPrecioVenta());
        assertEquals(PricingMode.MANUAL, unselected.getPricingMode());
        assertEquals(new BigDecimal("2.3000"), unselected.getManualMarkup());
        assertEquals(new BigDecimal("950"), unselected.getPrecioVenta());
        verify(discoRepository).findAllById(List.of(1L));
    }

    @Test
    void rejectsEmptySelectedIdList() {
        NegocioException error = assertThrows(
            NegocioException.class,
            () -> service.apply(new PricingApplyRequestDTO(defaultSettings(), "selected", List.of()))
        );

        assertEquals("Debés seleccionar al menos un disco", error.getMessage());
    }

    @Test
    void ignoresNonexistentSelectedIdsSafely() {
        when(discoRepository.findAllById(List.of(99L))).thenReturn(List.of());

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "selected", List.of(99L)));

        assertEquals(0, response.updatedCount());
    }

    @Test
    void persistsIndividualMarkupAndRecalculatesFinalPrice() {
        Disco disco = disco(1L, "Markup", new BigDecimal("1260"), PricingMode.AUTO, null);
        when(discoRepository.findById(1L)).thenReturn(Optional.of(disco));

        PricingMarkupUpdateResponseDTO response = service.updateDiscMarkup(1L, new PricingMarkupUpdateRequestDTO(new BigDecimal("2.0000")));

        assertEquals(1L, response.idDisco());
        assertEquals(new BigDecimal("2"), response.markup());
        assertEquals(new BigDecimal("1490"), response.finalSalePriceUyu());
        assertEquals("MANUAL", response.pricingMode());
        assertEquals(new BigDecimal("2"), disco.getManualMarkup());
        assertEquals(PricingMode.MANUAL, disco.getPricingMode());
        assertEquals(new BigDecimal("1490"), disco.getPrecioVenta());
    }

    @Test
    void preservesManualMarkupWhenApplyingOnlyAutomaticPrices() {
        Disco manual = disco(1L, "Manual", new BigDecimal("1450"), PricingMode.MANUAL, new BigDecimal("1.9500"));
        Disco automatic = disco(2L, "Auto", new BigDecimal("0"), PricingMode.AUTO, null);
        when(discoRepository.findAll()).thenReturn(List.of(manual, automatic));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "automatic", null));

        assertEquals(1, response.updatedCount());
        assertEquals(new BigDecimal("1450"), manual.getPrecioVenta());
        assertEquals(new BigDecimal("1.9500"), manual.getManualMarkup());
        assertEquals(PricingMode.MANUAL, manual.getPricingMode());
        assertEquals(PricingMode.AUTO, automatic.getPricingMode());
        assertNull(automatic.getManualMarkup());
        assertEquals(new BigDecimal("1260"), automatic.getPrecioVenta());
    }

    @Test
    void keepsApplyAllBehaviorWorking() {
        Disco manual = disco(1L, "Manual", new BigDecimal("1700"), PricingMode.MANUAL, new BigDecimal("2.1000"));
        when(discoRepository.findAll()).thenReturn(List.of(manual));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "all", null));

        assertEquals(1, response.updatedCount());
        assertEquals(PricingMode.AUTO, manual.getPricingMode());
        assertNull(manual.getManualMarkup());
        assertEquals(new BigDecimal("1260"), manual.getPrecioVenta());
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
        pricing.setRoundingRule(CatalogPricingService.DEFAULT_ROUNDING_RULE);
        return pricing;
    }

    private Disco disco(Long id, String album, BigDecimal precioVenta, PricingMode pricingMode, BigDecimal manualMarkup) {
        return Disco.builder()
            .idDisco(id)
            .codigoInterno("DISC-" + id)
            .artista("Artist")
            .album(album)
            .costo(new BigDecimal("10"))
            .cantidadCopias(1)
            .formato("LP")
            .precioVenta(precioVenta)
            .pricingMode(pricingMode)
            .manualMarkup(manualMarkup)
            .build();
    }
}
