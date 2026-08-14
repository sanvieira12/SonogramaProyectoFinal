package com.sonograma.service.crm;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.entity.*;
import com.sonograma.enums.EstadoVenta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class CrmProfileCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public CustomerProfile calculate(Cliente cliente, List<Venta> sourceSales, LocalDateTime asOf) {
        List<Venta> sales = sourceSales == null ? List.of() : sourceSales.stream()
                .filter(Objects::nonNull)
                .filter(v -> v.getEstado() == EstadoVenta.COMPLETADA
                        || v.getEstado() == EstadoVenta.CANCELADA)
                .filter(v -> v.getFechaVenta() != null)
                .sorted(Comparator.comparing(Venta::getFechaVenta).reversed())
                .toList();
        List<PurchaseLine> lines = sales.stream().flatMap(v -> lines(v).stream()).toList();
        LocalDateTime recentStart = asOf.minusMonths(12);
        List<PurchaseLine> recentLines = lines.stream()
                .filter(line -> !line.fechaCompra().isBefore(recentStart))
                .toList();

        Metrics metrics = metrics(sales, lines, recentStart);
        Taste historical = taste(lines);
        Taste recent = taste(recentLines);
        Set<Long> purchasedIds = new HashSet<>();
        lines.stream().map(PurchaseLine::idDisco).filter(Objects::nonNull).forEach(purchasedIds::add);
        return new CustomerProfile(cliente, metrics, historical, recent, lines, Set.copyOf(purchasedIds));
    }

    private List<PurchaseLine> lines(Venta sale) {
        BigDecimal factor = discountFactor(sale.getDescuentoPorcentaje());
        if (sale.getDetalles() != null && !sale.getDetalles().isEmpty()) {
            List<PurchaseLine> result = new ArrayList<>();
            Set<String> legacyManualKeys = new HashSet<>();
            for (DetalleVenta detail : sale.getDetalles()) {
                PurchaseLine line = fromDetail(sale, detail, factor);
                if (isLegacyManualSnapshot(detail)) {
                    String key = legacyManualKey(detail, line);
                    if (!legacyManualKeys.add(key)) continue;
                }
                result.add(line);
            }
            return List.copyOf(result);
        }
        if (sale.getDisco() == null) return List.of();
        BigDecimal unitPrice;
        if (sale.getSubtotal() != null) {
            unitPrice = sale.getSubtotal().multiply(factor);
        } else if (sale.getPrecioVenta() != null) {
            // Legacy sales store the already-resolved merchandise price here.
            unitPrice = sale.getPrecioVenta();
        } else {
            unitPrice = merchandiseFallback(sale);
        }
        return List.of(fromLegacy(sale, unitPrice));
    }

    private boolean isLegacyManualSnapshot(DetalleVenta detail) {
        return detail.getDisco() == null && Boolean.TRUE.equals(detail.getManualItem());
    }

    private String legacyManualKey(DetalleVenta detail, PurchaseLine line) {
        return String.join("|",
                CrmMetadataNormalizer.normalize(line.artista()),
                CrmMetadataNormalizer.normalize(line.album()),
                CrmMetadataNormalizer.normalize(detail.getCodigoSnap()),
                CrmMetadataNormalizer.normalize(detail.getDescripcionSnap()),
                line.precioUnitarioPagado() == null ? "" : line.precioUnitarioPagado().toPlainString(),
                String.valueOf(line.cantidad()));
    }

    private PurchaseLine fromDetail(Venta sale, DetalleVenta detail, BigDecimal factor) {
        Disco disc = detail.getDisco();
        int quantity = validQuantity(detail.getCantidad());
        BigDecimal netUnit = detail.getPrecioUnitario() == null ? null
                : money(detail.getPrecioUnitario().multiply(factor));
        return new PurchaseLine(
                sale.getIdVenta(), detail.getIdDetalle(), disc != null ? disc.getIdDisco() : null,
                sale.getFechaVenta(), quantity, netUnit,
                firstText(detail.getArtistaSnap(), disc != null ? disc.getArtista() : null),
                firstText(detail.getAlbumSnap(), disc != null ? disc.getAlbum() : null),
                disc != null ? disc.getGenero() : null,
                disc != null ? disc.getEstilo() : null,
                disc != null ? disc.getSelloDiscografico() : null,
                disc != null ? disc.getAnio() : null,
                disc != null ? disc.getFormato() : null,
                disc != null && disc.getCondicion() != null ? disc.getCondicion().name() : null,
                disc != null ? disc.getPais() : null,
                disc != null ? disc.getImagenUrl() : null,
                Boolean.TRUE.equals(detail.getManualItem())
        );
    }

    private PurchaseLine fromLegacy(Venta sale, BigDecimal netUnit) {
        Disco disc = sale.getDisco();
        return new PurchaseLine(
                sale.getIdVenta(), null, disc.getIdDisco(), sale.getFechaVenta(), 1,
                netUnit == null ? null : money(netUnit), disc.getArtista(), disc.getAlbum(), disc.getGenero(),
                disc.getEstilo(), disc.getSelloDiscografico(), disc.getAnio(), disc.getFormato(),
                disc.getCondicion() != null ? disc.getCondicion().name() : null,
                disc.getPais(), disc.getImagenUrl(), false
        );
    }

    private Metrics metrics(List<Venta> sales, List<PurchaseLine> lines, LocalDateTime recentStart) {
        long recordCount = lines.stream().mapToLong(PurchaseLine::cantidad).sum();
        BigDecimal spend = lines.stream()
                .filter(line -> line.precioUnitarioPagado() != null)
                .map(line -> line.precioUnitarioPagado().multiply(BigDecimal.valueOf(line.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> unitPrices = new ArrayList<>();
        for (PurchaseLine line : lines) {
            if (line.precioUnitarioPagado() == null || line.precioUnitarioPagado().compareTo(BigDecimal.ZERO) < 0) continue;
            for (int i = 0; i < line.cantidad(); i++) unitPrices.add(line.precioUnitarioPagado());
        }
        unitPrices.sort(BigDecimal::compareTo);
        BigDecimal averageOrder = sales.isEmpty() ? BigDecimal.ZERO
                : money(spend.divide(BigDecimal.valueOf(sales.size()), 8, RoundingMode.HALF_UP));
        BigDecimal averageUnit = unitPrices.isEmpty() ? null
                : money(unitPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(unitPrices.size()), 8, RoundingMode.HALF_UP));
        LocalDateTime first = sales.stream().map(Venta::getFechaVenta).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime last = sales.stream().map(Venta::getFechaVenta).max(Comparator.naturalOrder()).orElse(null);
        long recentTransactions = sales.stream().filter(v -> !v.getFechaVenta().isBefore(recentStart)).count();
        return new Metrics(
                sales.size(), recordCount, money(spend), averageOrder, averageUnit,
                percentile(unitPrices, .5), unitPrices.isEmpty() ? null : unitPrices.get(0),
                unitPrices.isEmpty() ? null : unitPrices.get(unitPrices.size() - 1),
                percentile(unitPrices, .25), percentile(unitPrices, .75), first, last,
                averageFrequencyDays(sales), recentTransactions, List.copyOf(unitPrices)
        );
    }

    private BigDecimal averageFrequencyDays(List<Venta> sales) {
        if (sales.size() < 2) return null;
        List<LocalDateTime> dates = sales.stream().map(Venta::getFechaVenta).sorted().toList();
        BigDecimal totalDays = BigDecimal.ZERO;
        for (int i = 1; i < dates.size(); i++) {
            BigDecimal days = BigDecimal.valueOf(Duration.between(dates.get(i - 1), dates.get(i)).toHours())
                    .divide(BigDecimal.valueOf(24), 8, RoundingMode.HALF_UP);
            totalDays = totalDays.add(days);
        }
        return totalDays.divide(BigDecimal.valueOf(dates.size() - 1L), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentile(List<BigDecimal> sorted, double percentile) {
        if (sorted.isEmpty()) return null;
        if (sorted.size() == 1) return money(sorted.get(0));
        double position = (sorted.size() - 1) * percentile;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return money(sorted.get(lower));
        BigDecimal fraction = BigDecimal.valueOf(position - lower);
        BigDecimal interpolated = sorted.get(lower)
                .add(sorted.get(upper).subtract(sorted.get(lower)).multiply(fraction));
        return money(interpolated);
    }

    private Taste taste(List<PurchaseLine> lines) {
        DimensionBuilder artists = new DimensionBuilder();
        DimensionBuilder genres = new DimensionBuilder();
        DimensionBuilder styles = new DimensionBuilder();
        DimensionBuilder labels = new DimensionBuilder();
        DimensionBuilder years = new DimensionBuilder();
        DimensionBuilder decades = new DimensionBuilder();
        DimensionBuilder formats = new DimensionBuilder();
        DimensionBuilder conditions = new DimensionBuilder();

        for (PurchaseLine line : lines) {
            int quantity = line.cantidad();
            CrmMetadataNormalizer.single(line.artista()).ifPresent(t -> artists.add(t, quantity));
            CrmMetadataNormalizer.split(line.genero()).forEach(t -> genres.add(t, quantity));
            CrmMetadataNormalizer.split(line.estilo()).forEach(t -> styles.add(t, quantity));
            CrmMetadataNormalizer.split(line.sello()).forEach(t -> labels.add(t, quantity));
            if (line.anio() != null) {
                CrmMetadataNormalizer.single(String.valueOf(line.anio())).ifPresent(t -> years.add(t, quantity));
                CrmMetadataNormalizer.single(CrmMetadataNormalizer.decade(line.anio())).ifPresent(t -> decades.add(t, quantity));
            }
            CrmMetadataNormalizer.format(line.formato()).ifPresent(t -> formats.add(t, quantity));
            CrmMetadataNormalizer.single(line.condicion()).ifPresent(t -> conditions.add(t, quantity));
        }
        return new Taste(artists.build(), genres.build(), styles.build(), labels.build(), years.build(),
                decades.build(), formats.build(), conditions.build());
    }

    public CrmDtos.Metricas metricsDto(Metrics m) {
        return new CrmDtos.Metricas(m.transactionCount(), m.recordCount(), m.totalSpend(), m.averageOrder(),
                m.averageUnitPrice(), m.medianUnitPrice(), m.minimumUnitPrice(), m.maximumUnitPrice(),
                m.typicalMinimum(), m.typicalMaximum(), m.firstPurchase(), m.lastPurchase(),
                m.averageFrequencyDays(), m.transactionsLast12Months());
    }

    public CrmDtos.Gusto tasteDto(Taste taste) {
        return new CrmDtos.Gusto(dto(taste.artists()), dto(taste.genres()), dto(taste.styles()),
                dto(taste.labels()), dto(taste.years()), dto(taste.decades()), dto(taste.formats()),
                dto(taste.conditions()));
    }

    public List<CrmDtos.Compra> historyDto(List<PurchaseLine> lines) {
        return lines.stream().map(line -> new CrmDtos.Compra(
                line.idVenta(), line.idDetalle(), line.idDisco(), line.artista(), line.album(), line.imagenUrl(),
                line.fechaCompra(), line.cantidad(), line.precioUnitarioPagado(),
                line.precioUnitarioPagado() == null ? null
                        : money(line.precioUnitarioPagado().multiply(BigDecimal.valueOf(line.cantidad()))),
                line.manualItem()
        )).toList();
    }

    private List<CrmDtos.Dimension> dto(Map<String, DimensionStat> values) {
        return values.values().stream().map(value -> new CrmDtos.Dimension(
                value.label(), value.count(), value.share().multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP)
        )).toList();
    }

    private BigDecimal discountFactor(BigDecimal discount) {
        BigDecimal safe = discount == null ? BigDecimal.ZERO : discount.max(BigDecimal.ZERO).min(ONE_HUNDRED);
        return ONE_HUNDRED.subtract(safe).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal merchandiseFallback(Venta sale) {
        BigDecimal stored = firstNonNull(sale.getTotalFinal(), sale.getTotal());
        if (stored == null) return null;
        BigDecimal shipping = sale.getCostoEnvio() == null ? BigDecimal.ZERO : sale.getCostoEnvio();
        BigDecimal taxes = sale.getMontoImpuesto() == null ? BigDecimal.ZERO : sale.getMontoImpuesto();
        BigDecimal otherCosts = sale.getOtrosCostos() == null ? BigDecimal.ZERO : sale.getOtrosCostos();
        return stored.subtract(shipping).subtract(taxes).subtract(otherCosts).max(BigDecimal.ZERO);
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) if (value != null) return value;
        return null;
    }

    private String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }

    private int validQuantity(Integer quantity) {
        return quantity != null && quantity > 0 ? quantity : 1;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DimensionBuilder {
        private final Map<String, MutableStat> values = new HashMap<>();

        void add(CrmMetadataNormalizer.Token token, long quantity) {
            values.computeIfAbsent(token.key(), ignored -> new MutableStat(token.label())).count += quantity;
        }

        Map<String, DimensionStat> build() {
            long total = values.values().stream().mapToLong(value -> value.count).sum();
            LinkedHashMap<String, DimensionStat> result = new LinkedHashMap<>();
            values.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, MutableStat>>comparingLong(entry -> entry.getValue().count)
                            .reversed().thenComparing(entry -> entry.getValue().label, String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> result.put(entry.getKey(), new DimensionStat(
                            entry.getKey(), entry.getValue().label, entry.getValue().count,
                            total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(entry.getValue().count)
                                    .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP)
                    )));
            return Collections.unmodifiableMap(result);
        }
    }

    private static final class MutableStat {
        private final String label;
        private long count;
        private MutableStat(String label) { this.label = label; }
    }

    public record DimensionStat(String key, String label, long count, BigDecimal share) {}
    public record Taste(
            Map<String, DimensionStat> artists,
            Map<String, DimensionStat> genres,
            Map<String, DimensionStat> styles,
            Map<String, DimensionStat> labels,
            Map<String, DimensionStat> years,
            Map<String, DimensionStat> decades,
            Map<String, DimensionStat> formats,
            Map<String, DimensionStat> conditions
    ) {}
    public record Metrics(
            long transactionCount,
            long recordCount,
            BigDecimal totalSpend,
            BigDecimal averageOrder,
            BigDecimal averageUnitPrice,
            BigDecimal medianUnitPrice,
            BigDecimal minimumUnitPrice,
            BigDecimal maximumUnitPrice,
            BigDecimal typicalMinimum,
            BigDecimal typicalMaximum,
            LocalDateTime firstPurchase,
            LocalDateTime lastPurchase,
            BigDecimal averageFrequencyDays,
            long transactionsLast12Months,
            List<BigDecimal> unitPrices
    ) {}
    public record PurchaseLine(
            Long idVenta,
            Long idDetalle,
            Long idDisco,
            LocalDateTime fechaCompra,
            int cantidad,
            BigDecimal precioUnitarioPagado,
            String artista,
            String album,
            String genero,
            String estilo,
            String sello,
            Integer anio,
            String formato,
            String condicion,
            String pais,
            String imagenUrl,
            boolean manualItem
    ) {}
    public record CustomerProfile(
            Cliente cliente,
            Metrics metrics,
            Taste historical,
            Taste recent,
            List<PurchaseLine> lines,
            Set<Long> purchasedDiscIds
    ) {}
}
