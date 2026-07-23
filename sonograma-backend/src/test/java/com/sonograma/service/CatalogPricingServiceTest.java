package com.sonograma.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.PricingApplyRequestDTO;
import com.sonograma.dto.PricingApplyResponseDTO;
import com.sonograma.dto.PricingMarkupUpdateRequestDTO;
import com.sonograma.dto.PricingMarkupUpdateResponseDTO;
import com.sonograma.dto.PricingPreviewResponseDTO;
import com.sonograma.dto.PricingSettingsUpdateDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.CondicionDisco;
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
import java.time.LocalDate;
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

    private static final BigDecimal TEST_RATE = new BigDecimal("49.37");
    private static final BigDecimal TEST_SINGLE_EXTRA = new BigDecimal("5.1256");
    private static final BigDecimal TEST_DOUBLE_EXTRA = new BigDecimal("8.3333");
    private static final BigDecimal TEST_MULTI_EXTRA = new BigDecimal("9.4444");
    private static final BigDecimal TEST_SINGLE_MARKUP = new BigDecimal("1.53");
    private static final BigDecimal TEST_DOUBLE_MARKUP = new BigDecimal("1.4567");
    private static final BigDecimal TEST_MULTI_MARKUP = new BigDecimal("1.4012");

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
        when(pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(pricingFrom(defaultSettings())));
        when(pricingSettingsRepository.save(any(PricingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.findByNumeroFacturaIn(anySet())).thenReturn(List.of());
    }

    @Test
    void calculatesSingleRecord() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("13.3644"), 1, "LP", pricingFrom(defaultSettings()));

        assertEquals(RecordType.SINGLE, result.recordType());
        assertEquals(0, new BigDecimal("13.3644").compareTo(result.unitLineTotalEur()));
        assertEquals(0, new BigDecimal("5.1256").compareTo(result.extraCostEur()));
        assertEquals(0, new BigDecimal("18.49").compareTo(result.realUnitCostEur()));
        assertEquals(0, new BigDecimal("912.8513").compareTo(result.realUnitCostUyu()));
        assertEquals(0, TEST_SINGLE_MARKUP.compareTo(result.markup()));
        assertEquals(0, new BigDecimal("1396.662489").compareTo(result.finalPriceUyu()));
    }

    @Test
    void quantityOnlyChangesStockAndNeverChangesUnitPricing() {
        CatalogPricingService.PricingResult quantityOne = service.calculate(
            new BigDecimal("12.29"), 1, "LP", pricingFrom(defaultSettings()));

        for (int quantity : List.of(2, 3, 10, 100)) {
            CatalogPricingService.PricingResult result = service.calculate(
                new BigDecimal("12.29"), quantity, "LP", pricingFrom(defaultSettings()));

            assertEquals(quantityOne.unitLineTotalEur(), result.unitLineTotalEur());
            assertEquals(quantityOne.extraCostEur(), result.extraCostEur());
            assertEquals(quantityOne.realUnitCostEur(), result.realUnitCostEur());
            assertEquals(quantityOne.realUnitCostUyu(), result.realUnitCostUyu());
            assertEquals(quantityOne.finalPriceUyu(), result.finalPriceUyu());
        }

        assertEquals(new BigDecimal("12.29"), quantityOne.unitLineTotalEur());
        assertEquals(TEST_SINGLE_EXTRA, quantityOne.extraCostEur());
        assertEquals(new BigDecimal("17.4156"), quantityOne.realUnitCostEur());
    }

    @Test
    void calculatesDoubleRecordFrom2x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("10.1234"), 1, "2x12\"", pricingFrom(defaultSettings()));

        assertEquals(RecordType.DOUBLE, result.recordType());
        assertEquals(0, new BigDecimal("8.3333").compareTo(result.extraCostEur()));
        assertEquals(0, new BigDecimal("18.4567").compareTo(result.realUnitCostEur()));
        assertEquals(0, new BigDecimal("911.207279").compareTo(result.realUnitCostUyu()));
        assertEquals(0, TEST_DOUBLE_MARKUP.compareTo(result.markup()));
        assertEquals(0, new BigDecimal("1327.3556433193").compareTo(result.finalPriceUyu()));
    }

    @Test
    void calculatesMultiRecordFrom3x12() {
        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("12.4321"), 1, "3x12\"", pricingFrom(defaultSettings()));

        assertEquals(RecordType.MULTI, result.recordType());
        assertEquals(0, new BigDecimal("9.4444").compareTo(result.extraCostEur()));
        assertEquals(0, new BigDecimal("21.8765").compareTo(result.realUnitCostEur()));
        assertEquals(0, new BigDecimal("1080.042805").compareTo(result.realUnitCostUyu()));
        assertEquals(0, TEST_MULTI_MARKUP.compareTo(result.markup()));
        assertEquals(0, new BigDecimal("1513.355978366").compareTo(result.finalPriceUyu()));
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
            TEST_SINGLE_EXTRA,
            TEST_DOUBLE_EXTRA,
            TEST_MULTI_EXTRA,
            TEST_SINGLE_MARKUP,
            TEST_DOUBLE_MARKUP,
            TEST_MULTI_MARKUP,
            PricingRoundingRule.NONE
        );

        PricingPreviewResponseDTO preview = service.preview(settings);
        assertNotNull(preview.settings());

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("13.3644"), 1, "LP", pricingFrom(settings));
        assertEquals(0, new BigDecimal("1016.95").compareTo(result.realUnitCostUyu()));
        assertEquals(0, new BigDecimal("1555.9335").compareTo(result.finalPriceUyu()));
    }

    @Test
    void recalculatesWithChangedMarkup() {
        PricingSettingsUpdateDTO settings = new PricingSettingsUpdateDTO(
            TEST_RATE,
            TEST_SINGLE_EXTRA,
            TEST_DOUBLE_EXTRA,
            TEST_MULTI_EXTRA,
            new BigDecimal("2.0001"),
            TEST_DOUBLE_MARKUP,
            TEST_MULTI_MARKUP,
            PricingRoundingRule.NEAREST_10
        );

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("13.3644"), 1, "LP", pricingFrom(settings));
        assertEquals(0, new BigDecimal("1825.79388513").compareTo(result.finalPriceUyu()));
    }

    @Test
    void ignoresLegacyRoundingRuleChangesAndKeepsRawDecimalFormula() {
        PricingSettingsUpdateDTO settings = new PricingSettingsUpdateDTO(
            TEST_RATE,
            TEST_SINGLE_EXTRA,
            TEST_DOUBLE_EXTRA,
            TEST_MULTI_EXTRA,
            TEST_SINGLE_MARKUP,
            TEST_DOUBLE_MARKUP,
            TEST_MULTI_MARKUP,
            PricingRoundingRule.NEAREST_100
        );

        CatalogPricingService.PricingResult result = service.calculate(new BigDecimal("13.3644"), 1, "LP", pricingFrom(settings));
        assertEquals(0, new BigDecimal("1396.662489").compareTo(result.finalPriceUyu()));
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
        selected.setCosto(new BigDecimal("13.3644"));
        Disco unselected = disco(2L, "Unselected", new BigDecimal("950"), PricingMode.MANUAL, new BigDecimal("2.3000"));
        when(discoRepository.findAllById(List.of(1L))).thenReturn(List.of(selected));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "selected", List.of(1L)));

        assertEquals(1, response.updatedCount());
        assertEquals(PricingMode.AUTO, selected.getPricingMode());
        assertNull(selected.getManualMarkup());
        assertEquals(0, new BigDecimal("1396.662489").compareTo(selected.getPrecioVenta()));
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
        disco.setCosto(new BigDecimal("13.3644"));
        when(discoRepository.findById(1L)).thenReturn(Optional.of(disco));

        PricingMarkupUpdateResponseDTO response = service.updateDiscMarkup(1L, new PricingMarkupUpdateRequestDTO(new BigDecimal("1.53098765")));

        assertEquals(1L, response.idDisco());
        assertEquals(0, new BigDecimal("1.53098765").compareTo(response.markup()));
        assertEquals(0, new BigDecimal("1397.564066586445").compareTo(response.finalSalePriceUyu()));
        assertEquals("MANUAL", response.pricingMode());
        assertEquals(0, new BigDecimal("1.53098765").compareTo(disco.getManualMarkup()));
        assertEquals(PricingMode.MANUAL, disco.getPricingMode());
        assertEquals(0, new BigDecimal("1397.564066586445").compareTo(disco.getPrecioVenta()));
    }

    @Test
    void preservesManualMarkupWhenApplyingOnlyAutomaticPrices() {
        Disco manual = disco(1L, "Manual", new BigDecimal("1450"), PricingMode.MANUAL, new BigDecimal("1.9500"));
        Disco automatic = disco(2L, "Auto", new BigDecimal("0"), PricingMode.AUTO, null);
        automatic.setCosto(new BigDecimal("13.3644"));
        when(discoRepository.findAll()).thenReturn(List.of(manual, automatic));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "automatic", null));

        assertEquals(1, response.updatedCount());
        assertEquals(new BigDecimal("1450"), manual.getPrecioVenta());
        assertEquals(new BigDecimal("1.9500"), manual.getManualMarkup());
        assertEquals(PricingMode.MANUAL, manual.getPricingMode());
        assertEquals(PricingMode.AUTO, automatic.getPricingMode());
        assertNull(automatic.getManualMarkup());
        assertEquals(0, new BigDecimal("1396.662489").compareTo(automatic.getPrecioVenta()));
    }

    @Test
    void keepsApplyAllBehaviorWorking() {
        Disco manual = disco(1L, "Manual", new BigDecimal("1700"), PricingMode.MANUAL, new BigDecimal("2.1000"));
        manual.setCosto(new BigDecimal("13.3644"));
        when(discoRepository.findAll()).thenReturn(List.of(manual));

        PricingApplyResponseDTO response = service.apply(new PricingApplyRequestDTO(defaultSettings(), "all", null));

        assertEquals(1, response.updatedCount());
        assertEquals(PricingMode.AUTO, manual.getPricingMode());
        assertNull(manual.getManualMarkup());
        assertEquals(0, new BigDecimal("1396.662489").compareTo(manual.getPrecioVenta()));
    }

    @Test
    void previewApplyAndSelectedKeepExactDecimalsAcrossOperations() throws Exception {
        PricingSettingsUpdateDTO settings = defaultSettings();
        Disco auto = disco(1L, "Auto", BigDecimal.ZERO, PricingMode.AUTO, null);
        auto.setCosto(new BigDecimal("13.3644"));
        Disco manual = disco(2L, "Manual", new BigDecimal("1700.000001"), PricingMode.MANUAL, new BigDecimal("2.00000001"));
        manual.setCosto(new BigDecimal("13.3644"));
        when(discoRepository.findAll()).thenReturn(List.of(auto, manual));
        when(discoRepository.findAllById(List.of(1L))).thenReturn(List.of(auto));

        PricingPreviewResponseDTO preview = service.preview(settings);
        BigDecimal expectedFinalPrice = new BigDecimal("1396.662489");

        assertEquals(0, expectedFinalPrice.compareTo(preview.rows().get(0).finalSalePriceUyu()));
        assertEquals(0, TEST_SINGLE_MARKUP.compareTo(preview.rows().get(0).markup()));

        PricingApplyResponseDTO automaticResponse = service.apply(new PricingApplyRequestDTO(settings, "automatic", null));
        assertEquals(1, automaticResponse.updatedCount());
        assertEquals(0, expectedFinalPrice.compareTo(auto.getPrecioVenta()));
        assertEquals(0, new BigDecimal("1700.000001").compareTo(manual.getPrecioVenta()));

        manual.setPrecioVenta(new BigDecimal("1400.123456"));
        PricingApplyResponseDTO allResponse = service.apply(new PricingApplyRequestDTO(settings, "all", null));
        assertEquals(2, allResponse.updatedCount());
        assertEquals(0, expectedFinalPrice.compareTo(auto.getPrecioVenta()));
        assertEquals(0, expectedFinalPrice.compareTo(manual.getPrecioVenta()));

        manual.setPrecioVenta(new BigDecimal("1700.000001"));
        manual.setPricingMode(PricingMode.MANUAL);
        manual.setManualMarkup(new BigDecimal("2.00000001"));
        auto.setPrecioVenta(BigDecimal.ZERO);
        PricingApplyResponseDTO selectedResponse = service.apply(new PricingApplyRequestDTO(settings, "selected", List.of(1L)));
        assertEquals(1, selectedResponse.updatedCount());
        assertEquals(0, expectedFinalPrice.compareTo(auto.getPrecioVenta()));
        assertEquals(0, new BigDecimal("1700.000001").compareTo(manual.getPrecioVenta()));
        assertEquals(0, new BigDecimal("2.00000001").compareTo(manual.getManualMarkup()));

        String json = new ObjectMapper().writeValueAsString(preview);
        assertTrue(json.contains("\"finalSalePriceUyu\":1396.662489"));
        assertTrue(json.contains("\"markup\":1.53"));
    }

    @Test
    void previewNormalizesFutureSupplierAndShippingFromStoredSource() {
        Disco disco = disco(1L, "Future Album", BigDecimal.ZERO, PricingMode.AUTO, null);
        disco.setProcedencia("VINYL_FUTURE");
        when(discoRepository.findAll()).thenReturn(List.of(disco));

        PricingPreviewResponseDTO preview = service.preview(defaultSettings());

        assertEquals("Future", preview.rows().get(0).supplier());
        assertEquals("UPS", preview.rows().get(0).shipping());
    }

    @Test
    void previewUsesDiscogsSourceAndPreservesRealShippingValue() {
        Disco disco = disco(1L, "Discogs Album", BigDecimal.ZERO, PricingMode.AUTO, null);
        disco.setProcedencia("DISCOGS");
        disco.setNumeroFacturaCompra("INV-1");
        Pedido pedido = Pedido.builder()
            .numeroFactura("INV-1")
            .proveedor("Discogs")
            .envio("Correo certificado")
            .fechaFactura(LocalDate.of(2026, 7, 10))
            .build();
        when(discoRepository.findAll()).thenReturn(List.of(disco));
        when(pedidoRepository.findByNumeroFacturaIn(anySet())).thenReturn(List.of(pedido));

        PricingPreviewResponseDTO preview = service.preview(defaultSettings());

        assertEquals("Discogs", preview.rows().get(0).supplier());
        assertEquals("Correo certificado", preview.rows().get(0).shipping());
    }

    @Test
    void previewIncludesStoredConditionWithoutChangingIt() {
        Disco disco = disco(1L, "Condition Album", BigDecimal.ZERO, PricingMode.AUTO, null);
        disco.setCondicion(CondicionDisco.USADO);
        when(discoRepository.findAll()).thenReturn(List.of(disco));

        PricingPreviewResponseDTO preview = service.preview(defaultSettings());

        assertEquals("USADO", preview.rows().get(0).condicion());
    }

    private PricingSettingsUpdateDTO defaultSettings() {
        return new PricingSettingsUpdateDTO(
            TEST_RATE,
            TEST_SINGLE_EXTRA,
            TEST_DOUBLE_EXTRA,
            TEST_MULTI_EXTRA,
            TEST_SINGLE_MARKUP,
            TEST_DOUBLE_MARKUP,
            TEST_MULTI_MARKUP,
            PricingRoundingRule.NONE
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
