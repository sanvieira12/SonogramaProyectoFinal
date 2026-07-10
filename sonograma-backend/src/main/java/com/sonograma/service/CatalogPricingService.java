package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.PricingApplyRequestDTO;
import com.sonograma.dto.PricingApplyResponseDTO;
import com.sonograma.dto.PricingMarkupUpdateRequestDTO;
import com.sonograma.dto.PricingMarkupUpdateResponseDTO;
import com.sonograma.dto.PricingPreviewResponseDTO;
import com.sonograma.dto.PricingPreviewRowDTO;
import com.sonograma.dto.PricingSettingsDTO;
import com.sonograma.dto.PricingSettingsUpdateDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.PricingRoundingRule;
import com.sonograma.enums.RecordType;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PedidoRepository;
import com.sonograma.repository.PricingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogPricingService {

    public static final BigDecimal DEFAULT_EUR_UYU_RATE = new BigDecimal("49.5");
    public static final BigDecimal DEFAULT_EXTRA_SINGLE = new BigDecimal("5");
    public static final BigDecimal DEFAULT_EXTRA_DOUBLE = new BigDecimal("8");
    public static final BigDecimal DEFAULT_EXTRA_MULTI = new BigDecimal("9");
    public static final BigDecimal DEFAULT_MARKUP_SINGLE = new BigDecimal("1.7");
    public static final BigDecimal DEFAULT_MARKUP_DOUBLE = new BigDecimal("1.5");
    public static final BigDecimal DEFAULT_MARKUP_MULTI = new BigDecimal("1.4");
    public static final PricingRoundingRule DEFAULT_ROUNDING_RULE = PricingRoundingRule.NONE;
    public static final BigDecimal TIPO_CAMBIO = DEFAULT_EUR_UYU_RATE;
    public static final BigDecimal EXTRA_SIMPLE = DEFAULT_EXTRA_SINGLE;
    public static final BigDecimal EXTRA_DOBLE = DEFAULT_EXTRA_DOUBLE;
    public static final BigDecimal MARKUP_SIMPLE = DEFAULT_MARKUP_SINGLE;
    public static final BigDecimal MARKUP_DOBLE = DEFAULT_MARKUP_DOUBLE;
    private static final int LEGACY_MARKUP_DIVISION_SCALE = 12;

    private final PricingSettingsRepository pricingSettingsRepository;
    private final DiscoRepository discoRepository;
    private final PedidoRepository pedidoRepository;

    @Transactional(readOnly = true)
    public PricingSettings getOrCreateSettings() {
        return pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)
            .orElseGet(this::defaultSettings);
    }

    @Transactional(readOnly = true)
    public PricingSettingsDTO getSettingsDto() {
        return toDto(getOrCreateSettings());
    }

    public PricingSettingsDTO updateSettings(PricingSettingsUpdateDTO request) {
        validateSettings(request);
        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)
            .orElseGet(this::defaultSettings);
        applySettings(settings, request);
        return toDto(pricingSettingsRepository.save(settings));
    }

    public PricingSettingsDTO resetToDefaults() {
        PricingSettings settings = defaultSettings();
        settings.setUpdatedAt(null);
        return toDto(pricingSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public PricingPreviewResponseDTO preview(PricingSettingsUpdateDTO request) {
        validateSettings(request);
        PricingSettings settings = fromRequest(request);
        List<Disco> discos = discoRepository.findAll();
        Map<String, Pedido> pedidosByInvoice = pedidosByInvoiceNumber(discos);
        List<PricingPreviewRowDTO> rows = discos.stream()
            .map(disco -> toPreviewRow(disco, settings, pedidosByInvoice))
            .toList();
        return new PricingPreviewResponseDTO(toDto(settings), rows);
    }

    public PricingApplyResponseDTO apply(PricingApplyRequestDTO request) {
        validateSettings(request.settings());
        Scope scope = parseScope(request.scope());
        List<Long> selectedIds = normalizeSelectedIds(request.selectedIds(), scope == Scope.SELECTED);

        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)
            .orElseGet(this::defaultSettings);
        applySettings(settings, request.settings());
        PricingSettings savedSettings = pricingSettingsRepository.save(settings);

        List<Disco> discos = loadTargetDiscos(scope, selectedIds);
        int updated = 0;
        for (Disco disco : discos) {
            PricingMode mode = disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO;
            if (scope == Scope.AUTOMATIC && mode == PricingMode.MANUAL) {
                continue;
            }
            PricingResult result = calculate(
                disco.getCosto(),
                disco.getCantidadCopias(),
                disco.getFormato(),
                savedSettings
            );
            if (result == null) {
                continue;
            }
            disco.setPrecioVenta(result.finalPriceUyu());
            disco.setPricingMode(PricingMode.AUTO);
            disco.setManualMarkup(null);
            updated++;
        }
        if (!discos.isEmpty()) {
            discoRepository.saveAll(discos);
        }
        return new PricingApplyResponseDTO(toDto(savedSettings), updated);
    }

    public PricingMarkupUpdateResponseDTO updateDiscMarkup(Long id, PricingMarkupUpdateRequestDTO request) {
        BigDecimal markup = normalizeMarkup(request.markup(), "El markup debe ser mayor a 0");
        Disco disco = discoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        PricingSettings settings = getOrCreateSettings();
        PricingResult result = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato(), settings, markup);
        if (result == null) {
            throw new NegocioException("No se pudo recalcular el precio final del disco seleccionado");
        }
        disco.setManualMarkup(markup);
        disco.setPricingMode(PricingMode.MANUAL);
        disco.setPrecioVenta(result.finalPriceUyu());
        Disco saved = discoRepository.save(disco);
        return new PricingMarkupUpdateResponseDTO(
            saved.getIdDisco(),
            markup,
            saved.getPrecioVenta(),
            saved.getPricingMode().name()
        );
    }

    @Transactional(readOnly = true)
    public PricingResult calculate(BigDecimal unitPriceEur, String format) {
        return calculate(unitPriceEur, 1, format, getOrCreateSettings());
    }

    @Transactional(readOnly = true)
    public PricingResult calcular(BigDecimal unitPriceEur, String format) {
        return calculate(unitPriceEur, format);
    }

    @Transactional(readOnly = true)
    public PricingResult calculate(BigDecimal unitPriceEur, Integer quantity, String format) {
        return calculate(unitPriceEur, quantity, format, getOrCreateSettings());
    }

    @Transactional(readOnly = true)
    public PricingResult calcular(BigDecimal unitPriceEur, Integer quantity, String format) {
        return calculate(unitPriceEur, quantity, format);
    }

    public PricingResult calculate(BigDecimal unitPriceEur, Integer quantity, String format, PricingSettings settings) {
        return calculate(unitPriceEur, quantity, format, settings, null);
    }

    public PricingResult calculate(
        BigDecimal unitPriceEur,
        Integer quantity,
        String format,
        PricingSettings settings,
        BigDecimal markupOverride
    ) {
        if (unitPriceEur == null || unitPriceEur.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        RecordType recordType = detectRecordType(format);
        int normalizedQuantity = normalizeQuantity(quantity);
        BigDecimal extra = extraForRecordType(recordType, settings);
        BigDecimal markup = markupOverride != null ? markupOverride : markupForRecordType(recordType, settings);

        BigDecimal quantityFactor = BigDecimal.valueOf(normalizedQuantity);
        BigDecimal lineTotal = unitPriceEur.multiply(quantityFactor);
        BigDecimal extraLineCostEur = extra.multiply(quantityFactor);
        BigDecimal realLineCostEur = lineTotal.add(extraLineCostEur);
        BigDecimal realLineCostUyu = realLineCostEur.multiply(settings.getEurUyuRate());
        BigDecimal finalPriceUyu = unitPriceEur.add(extra).multiply(settings.getEurUyuRate()).multiply(markup);

        return new PricingResult(recordType, lineTotal, extraLineCostEur, realLineCostEur, realLineCostUyu, markup, finalPriceUyu);
    }

    public RecordType detectRecordType(String format) {
        String normalized = normalizeFormat(format);
        String compact = normalized.replace(" ", "").replace("'", "").replace("\"", "");
        if (normalized.isBlank()) {
            return RecordType.SINGLE;
        }
        if (compact.contains("boxset") || normalized.contains("box set") || compact.startsWith("3x")
            || compact.startsWith("4x") || compact.startsWith("5x") || compact.contains("3lp")
            || compact.contains("4lp") || compact.contains("5lp") || compact.contains("multi")) {
            return RecordType.MULTI;
        }
        if (isDoubleAlbum(normalized, compact)) {
            return RecordType.DOUBLE;
        }
        return RecordType.SINGLE;
    }

    public boolean esDoble(String format) {
        return detectRecordType(format) == RecordType.DOUBLE;
    }

    public boolean isManualOverride(Disco disco, DiscoRequestDTO request) {
        if (request.getPricingMode() == PricingMode.MANUAL) {
            return true;
        }
        if (request.getPrecioVenta() == null) {
            return false;
        }
        BigDecimal current = disco.getPrecioVenta();
        return current != null && current.compareTo(request.getPrecioVenta()) != 0;
    }

    public void applyPricingToDisco(Disco disco, DiscoRequestDTO request) {
        PricingMode currentMode = disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO;
        PricingMode requestedMode = request.getPricingMode();

        if (requestedMode == null && request.getPrecioVenta() != null && request.getCosto() == null) {
            disco.setPricingMode(PricingMode.MANUAL);
            return;
        }

        if (requestedMode == PricingMode.MANUAL || isManualOverride(disco, request)) {
            disco.setPricingMode(PricingMode.MANUAL);
            return;
        }

        if (requestedMode == PricingMode.AUTO || (request.getPrecioVenta() == null && request.getCosto() != null)) {
            PricingResult pricing = calculate(request.getCosto(), request.getCantidadCopias(), request.getFormato());
            if (pricing != null) {
                disco.setPrecioVenta(pricing.finalPriceUyu());
                disco.setPricingMode(PricingMode.AUTO);
                disco.setManualMarkup(null);
                return;
            }
        }

        if (currentMode == PricingMode.AUTO && disco.getCosto() != null && disco.getPrecioVenta() == null) {
            PricingResult pricing = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato());
            if (pricing != null) {
                disco.setPrecioVenta(pricing.finalPriceUyu());
                disco.setManualMarkup(null);
            }
        }
    }

    public void enrichDiscoResponse(Disco disco, DiscoResponseDTO dto) {
        PricingSettings settings = getOrCreateSettings();
        PricingResult pricing = calculateForDisco(disco, settings);
        if (pricing == null) {
            return;
        }
        dto.setRecordType(pricing.recordType().name());
        dto.setUnitLineTotalEur(pricing.unitLineTotalEur());
        dto.setExtraCostEur(pricing.extraCostEur());
        dto.setRealUnitCostEur(pricing.realUnitCostEur());
        dto.setRealUnitCostUyu(pricing.realUnitCostUyu());
        dto.setPricingMarkup(pricing.markup());
    }

    public PricingPreviewRowDTO toPreviewRow(Disco disco, PricingSettings settings, Map<String, Pedido> pedidosByInvoice) {
        PricingResult pricing = calculateForDisco(disco, settings);
        Pedido pedido = pedidoForDisco(disco, pedidosByInvoice);
        String supplier = ImportMetadataNormalizer.resolveDisplaySupplier(
            disco.getProcedencia(),
            pedido != null ? pedido.getProveedor() : null
        );
        String shipping = ImportMetadataNormalizer.normalizeShipping(
            supplier,
            pedido != null ? pedido.getEnvio() : null
        );
        return new PricingPreviewRowDTO(
            disco.getIdDisco(),
            blankToNull(disco.getNumeroFacturaCompra()),
            disco.getFechaFacturaCompra(),
            supplier,
            shipping,
            disco.getCodigoInterno(),
            disco.getArtista(),
            disco.getAlbum(),
            disco.getFormato(),
            pricing != null ? pricing.recordType().name() : RecordType.SINGLE.name(),
            disco.getCosto(),
            normalizeQuantity(disco.getCantidadCopias()),
            pricing != null ? pricing.unitLineTotalEur() : null,
            pricing != null ? pricing.extraCostEur() : null,
            pricing != null ? pricing.realUnitCostEur() : null,
            pricing != null ? pricing.realUnitCostUyu() : null,
            pricing != null ? pricing.markup() : null,
            pricing != null ? pricing.finalPriceUyu() : disco.getPrecioVenta(),
            (disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO).name()
        );
    }

    private PricingResult calculateForDisco(Disco disco, PricingSettings settings) {
        BigDecimal markupOverride = effectiveMarkupOverride(disco, settings);
        return calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato(), settings, markupOverride);
    }

    private BigDecimal effectiveMarkupOverride(Disco disco, PricingSettings settings) {
        if (disco.getManualMarkup() != null) {
            return disco.getManualMarkup();
        }
        PricingMode mode = disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO;
        if (mode != PricingMode.MANUAL || disco.getPrecioVenta() == null || disco.getCosto() == null
            || disco.getCosto().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        RecordType recordType = detectRecordType(disco.getFormato());
        BigDecimal base = disco.getCosto()
            .add(extraForRecordType(recordType, settings))
            .multiply(settings.getEurUyuRate());
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // Legacy manual rows may not have manual_markup persisted. A bounded scale is only used
        // here to derive a usable ratio from the stored manual sale price when division is non-terminating.
        return disco.getPrecioVenta()
            .divide(base, LEGACY_MARKUP_DIVISION_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    }

    private BigDecimal extraForRecordType(RecordType recordType, PricingSettings settings) {
        return switch (recordType) {
            case DOUBLE -> settings.getExtraCostDoubleEur();
            case MULTI -> settings.getExtraCostMultiEur();
            case SINGLE -> settings.getExtraCostSingleEur();
        };
    }

    private BigDecimal markupForRecordType(RecordType recordType, PricingSettings settings) {
        return switch (recordType) {
            case DOUBLE -> settings.getMarkupDouble();
            case MULTI -> settings.getMarkupMulti();
            case SINGLE -> settings.getMarkupSingle();
        };
    }

    private List<Disco> loadTargetDiscos(Scope scope, List<Long> selectedIds) {
        if (scope == Scope.SELECTED) {
            return new ArrayList<>(discoRepository.findAllById(selectedIds));
        }
        return new ArrayList<>(discoRepository.findAll());
    }

    private Scope parseScope(String rawScope) {
        if (rawScope == null) {
            throw new NegocioException("El alcance es obligatorio");
        }
        return switch (rawScope.strip().toLowerCase(Locale.ROOT)) {
            case "automatic" -> Scope.AUTOMATIC;
            case "all" -> Scope.ALL;
            case "selected" -> Scope.SELECTED;
            default -> throw new NegocioException("El alcance debe ser 'automatic', 'all' o 'selected'");
        };
    }

    private List<Long> normalizeSelectedIds(List<Long> selectedIds, boolean required) {
        if (!required) {
            return selectedIds == null ? List.of() : selectedIds;
        }
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new NegocioException("Debés seleccionar al menos un disco");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : selectedIds) {
            if (id == null || id <= 0) {
                throw new NegocioException("Los IDs seleccionados son inválidos");
            }
            normalized.add(id);
        }
        return List.copyOf(normalized);
    }

    private Map<String, Pedido> pedidosByInvoiceNumber(List<Disco> discos) {
        Set<String> invoiceNumbers = discos.stream()
            .map(Disco::getNumeroFacturaCompra)
            .map(this::blankToNull)
            .filter(value -> value != null)
            .collect(Collectors.toSet());
        if (invoiceNumbers.isEmpty()) {
            return Collections.emptyMap();
        }
        return pedidoRepository.findByNumeroFacturaIn(invoiceNumbers).stream()
            .filter(pedido -> blankToNull(pedido.getNumeroFactura()) != null)
            .collect(Collectors.toMap(Pedido::getNumeroFactura, Function.identity(), (first, second) -> first));
    }

    private Pedido pedidoForDisco(Disco disco, Map<String, Pedido> pedidosByInvoice) {
        String invoiceNumber = blankToNull(disco.getNumeroFacturaCompra());
        if (invoiceNumber == null) {
            return null;
        }
        return pedidosByInvoice.get(invoiceNumber);
    }

    private int normalizeQuantity(Integer quantity) {
        return quantity != null && quantity > 0 ? quantity : 1;
    }

    private String normalizeFormat(String format) {
        return format == null ? "" : format.strip().toLowerCase(Locale.ROOT);
    }

    private boolean isDoubleAlbum(String normalized, String compact) {
        return compact.startsWith("2x")
            || compact.contains("2lp")
            || compact.contains("2xlp")
            || compact.contains("2x12")
            || compact.contains("double");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void validateSettings(PricingSettingsUpdateDTO request) {
        if (request.eurUyuRate() == null || request.eurUyuRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("La cotización EUR/UYU debe ser mayor a 0");
        }
        if (request.extraCostSingleEur() == null || request.extraCostSingleEur().compareTo(BigDecimal.ZERO) < 0
            || request.extraCostDoubleEur() == null || request.extraCostDoubleEur().compareTo(BigDecimal.ZERO) < 0
            || request.extraCostMultiEur() == null || request.extraCostMultiEur().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("Los costos extra no pueden ser negativos");
        }
        if (request.markupSingle() == null || request.markupSingle().compareTo(BigDecimal.ZERO) <= 0
            || request.markupDouble() == null || request.markupDouble().compareTo(BigDecimal.ZERO) <= 0
            || request.markupMulti() == null || request.markupMulti().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Los markups deben ser mayores a 0");
        }
    }

    private BigDecimal normalizeMarkup(BigDecimal markup, String message) {
        if (markup == null || markup.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException(message);
        }
        return markup.round(MathContext.DECIMAL128).stripTrailingZeros();
    }

    private PricingSettings defaultSettings() {
        return PricingSettings.builder()
            .id(PricingSettings.SINGLETON_ID)
            .eurUyuRate(DEFAULT_EUR_UYU_RATE)
            .extraCostSingleEur(DEFAULT_EXTRA_SINGLE)
            .extraCostDoubleEur(DEFAULT_EXTRA_DOUBLE)
            .extraCostMultiEur(DEFAULT_EXTRA_MULTI)
            .markupSingle(DEFAULT_MARKUP_SINGLE)
            .markupDouble(DEFAULT_MARKUP_DOUBLE)
            .markupMulti(DEFAULT_MARKUP_MULTI)
            .roundingRule(DEFAULT_ROUNDING_RULE)
            .build();
    }

    private PricingSettings fromRequest(PricingSettingsUpdateDTO request) {
        PricingSettings settings = defaultSettings();
        applySettings(settings, request);
        return settings;
    }

    private void applySettings(PricingSettings settings, PricingSettingsUpdateDTO request) {
        settings.setEurUyuRate(request.eurUyuRate());
        settings.setExtraCostSingleEur(request.extraCostSingleEur());
        settings.setExtraCostDoubleEur(request.extraCostDoubleEur());
        settings.setExtraCostMultiEur(request.extraCostMultiEur());
        settings.setMarkupSingle(request.markupSingle());
        settings.setMarkupDouble(request.markupDouble());
        settings.setMarkupMulti(request.markupMulti());
        settings.setRoundingRule(DEFAULT_ROUNDING_RULE);
    }

    private PricingSettingsDTO toDto(PricingSettings settings) {
        return new PricingSettingsDTO(
            settings.getId(),
            settings.getEurUyuRate(),
            settings.getExtraCostSingleEur(),
            settings.getExtraCostDoubleEur(),
            settings.getExtraCostMultiEur(),
            settings.getMarkupSingle(),
            settings.getMarkupDouble(),
            settings.getMarkupMulti(),
            settings.getRoundingRule(),
            settings.getUpdatedAt()
        );
    }

    public record PricingResult(
        RecordType recordType,
        BigDecimal unitLineTotalEur,
        BigDecimal extraCostEur,
        BigDecimal realUnitCostEur,
        BigDecimal realUnitCostUyu,
        BigDecimal markup,
        BigDecimal finalPriceUyu
    ) {
        public PricingResult(
            BigDecimal extraCostEur,
            BigDecimal realUnitCostEur,
            BigDecimal realUnitCostUyu,
            BigDecimal markup,
            BigDecimal finalPriceUyu
        ) {
            this(RecordType.SINGLE, null, extraCostEur, realUnitCostEur, realUnitCostUyu, markup, finalPriceUyu);
        }
    }

    private enum Scope {
        AUTOMATIC,
        ALL,
        SELECTED
    }
}
