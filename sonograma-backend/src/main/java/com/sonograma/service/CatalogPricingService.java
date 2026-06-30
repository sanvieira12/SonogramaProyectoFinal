package com.sonograma.service;

import com.sonograma.dto.*;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PricingSettings;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.PricingRoundingRule;
import com.sonograma.enums.RecordType;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PricingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

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
    public static final PricingRoundingRule DEFAULT_ROUNDING_RULE = PricingRoundingRule.NEAREST_10;
    public static final BigDecimal TIPO_CAMBIO = DEFAULT_EUR_UYU_RATE;
    public static final BigDecimal EXTRA_SIMPLE = DEFAULT_EXTRA_SINGLE;
    public static final BigDecimal EXTRA_DOBLE = DEFAULT_EXTRA_DOUBLE;
    public static final BigDecimal MARKUP_SIMPLE = DEFAULT_MARKUP_SINGLE;
    public static final BigDecimal MARKUP_DOBLE = DEFAULT_MARKUP_DOUBLE;

    private final PricingSettingsRepository pricingSettingsRepository;
    private final DiscoRepository discoRepository;

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
        List<PricingPreviewRowDTO> rows = discoRepository.findAll().stream()
            .map(disco -> toPreviewRow(disco, settings))
            .toList();
        return new PricingPreviewResponseDTO(toDto(settings), rows);
    }

    public PricingApplyResponseDTO apply(PricingApplyRequestDTO request) {
        validateSettings(request.settings());
        boolean includeManual = "all".equalsIgnoreCase(request.scope());
        if (!includeManual && !"automatic".equalsIgnoreCase(request.scope())) {
            throw new NegocioException("El alcance debe ser 'automatic' o 'all'");
        }

        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID)
            .orElseGet(this::defaultSettings);
        applySettings(settings, request.settings());
        PricingSettings savedSettings = pricingSettingsRepository.save(settings);

        int updated = 0;
        List<Disco> discos = discoRepository.findAll();
        for (Disco disco : discos) {
            PricingMode mode = disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO;
            if (!includeManual && mode == PricingMode.MANUAL) {
                continue;
            }
            PricingResult result = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato(), savedSettings);
            if (result == null) {
                continue;
            }
            disco.setPrecioVenta(result.finalPriceUyu());
            if (includeManual || mode == PricingMode.AUTO) {
                disco.setPricingMode(PricingMode.AUTO);
            }
            updated++;
        }
        discoRepository.saveAll(discos);
        return new PricingApplyResponseDTO(toDto(savedSettings), updated);
    }

    @Transactional(readOnly = true)
    public PricingResult calculate(BigDecimal unitPriceEur, String format) {
        return calculate(unitPriceEur, 1, format, getOrCreateSettings());
    }

    @Transactional(readOnly = true)
    public PricingResult calculate(BigDecimal unitPriceEur, Integer quantity, String format) {
        return calculate(unitPriceEur, quantity, format, getOrCreateSettings());
    }

    public PricingResult calculate(BigDecimal unitPriceEur, Integer quantity, String format, PricingSettings settings) {
        if (unitPriceEur == null || unitPriceEur.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        RecordType recordType = detectRecordType(format);
        BigDecimal extra = switch (recordType) {
            case DOUBLE -> settings.getExtraCostDoubleEur();
            case MULTI -> settings.getExtraCostMultiEur();
            case SINGLE -> settings.getExtraCostSingleEur();
        };
        BigDecimal markup = switch (recordType) {
            case DOUBLE -> settings.getMarkupDouble();
            case MULTI -> settings.getMarkupMulti();
            case SINGLE -> settings.getMarkupSingle();
        };

        BigDecimal lineTotal = unitPriceEur.multiply(BigDecimal.valueOf(normalizeQuantity(quantity))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realUnitCostEur = unitPriceEur.add(extra).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realUnitCostUyu = realUnitCostEur.multiply(settings.getEurUyuRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPriceUyu = applyRounding(realUnitCostUyu.multiply(markup), settings.getRoundingRule());

        return new PricingResult(recordType, lineTotal, extra, realUnitCostEur, realUnitCostUyu, markup, finalPriceUyu);
    }

    public RecordType detectRecordType(String format) {
        String normalized = normalizeFormat(format);
        if (normalized.isBlank()) {
            return RecordType.SINGLE;
        }
        if (normalized.contains("boxset") || normalized.contains("box set") || normalized.contains("box")
            || normalized.contains("multi") || normalized.startsWith("3x") || normalized.startsWith("4x")
            || normalized.contains("3lp") || normalized.contains("4lp")) {
            return RecordType.MULTI;
        }
        if (normalized.startsWith("2x") || normalized.contains("2lp") || normalized.contains("2xlp")
            || normalized.contains("double")) {
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
                return;
            }
        }

        if (currentMode == PricingMode.AUTO && disco.getCosto() != null && disco.getPrecioVenta() == null) {
            PricingResult pricing = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato());
            if (pricing != null) {
                disco.setPrecioVenta(pricing.finalPriceUyu());
            }
        }
    }

    public void enrichDiscoResponse(Disco disco, DiscoResponseDTO dto) {
        PricingResult pricing = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato());
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

    public PricingPreviewRowDTO toPreviewRow(Disco disco, PricingSettings settings) {
        PricingResult pricing = calculate(disco.getCosto(), disco.getCantidadCopias(), disco.getFormato(), settings);
        return new PricingPreviewRowDTO(
            disco.getIdDisco(),
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
            pricing != null ? pricing.finalPriceUyu() : null,
            (disco.getPricingMode() != null ? disco.getPricingMode() : PricingMode.AUTO).name()
        );
    }

    private BigDecimal applyRounding(BigDecimal value, PricingRoundingRule rule) {
        if (value == null) {
            return null;
        }
        return switch (rule) {
            case NONE -> value.setScale(2, RoundingMode.HALF_UP);
            case NEAREST_10 -> roundToNearest(value, BigDecimal.TEN);
            case NEAREST_50 -> roundToNearest(value, new BigDecimal("50"));
            case NEAREST_100 -> roundToNearest(value, new BigDecimal("100"));
        };
    }

    private BigDecimal roundToNearest(BigDecimal value, BigDecimal increment) {
        return value.divide(increment, 0, RoundingMode.HALF_UP).multiply(increment).setScale(0, RoundingMode.HALF_UP);
    }

    private int normalizeQuantity(Integer quantity) {
        return quantity != null && quantity > 0 ? quantity : 1;
    }

    private String normalizeFormat(String format) {
        return format == null ? "" : format.strip().toLowerCase(Locale.ROOT);
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
        settings.setRoundingRule(request.roundingRule());
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
    ) {}
}
