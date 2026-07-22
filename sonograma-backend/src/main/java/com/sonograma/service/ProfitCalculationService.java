package com.sonograma.service;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.PedidoItemRepository;
import com.sonograma.repository.PedidoRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;

/**
 * Single source of truth for historical sale profit.
 *
 * This service never reads Disco.precioVenta. The sold price comes from the
 * sale/detail and the acquisition cost comes from the normalized detail
 * snapshot, or from a strictly recoverable historical purchase record for
 * legacy rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfitCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final int COST_SCALE = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final VentaRepository ventaRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;

    /** Calculates one line using its recorded unit sale amount without sale-level allocation. */
    public ProfitItemResult netProfitForSoldItem(DetalleVenta detalle) {
        Objects.requireNonNull(detalle, "detalle");
        int quantity = quantityOf(detalle);
        BigDecimal actualAmount = money(nvl(detalle.getPrecioUnitario())
                .multiply(BigDecimal.valueOf(quantity)));
        return calculateItem(detalle, actualAmount, null);
    }

    /** Resolves one catalog item's immutable acquisition cost in UYU. */
    public AcquisitionCostResolution acquisitionCostForDisco(Disco disco) {
        if (disco == null) {
            return unavailableCost();
        }

        String currency = catalogCurrency(disco);
        Optional<PedidoItem> purchaseItem = purchaseItemFor(disco);
        if (purchaseItem.isPresent()) {
            PedidoItem item = purchaseItem.get();
            Pedido purchase = item.getPedido();
            BigDecimal rate = purchase != null ? positive(purchase.getTipoCambio()) : null;
            BigDecimal landedUyu = positiveOrZero(item.getCostoRealUyu());
            if (landedUyu != null) {
                return new AcquisitionCostResolution(
                        cost(landedUyu),
                        firstNonNull(item.getCostoRealEur(), disco.getCosto()),
                        purchaseCurrency(purchase, currency),
                        rate,
                        "HISTORICAL_PURCHASE_LANDED_COST");
            }
            BigDecimal historicalEur = firstNonNull(item.getCostoRealEur(), disco.getCosto());
            if (isForeign(currency) && rate != null && historicalEur != null) {
                return new AcquisitionCostResolution(
                        cost(historicalEur.multiply(rate)), historicalEur, currency, rate,
                        "HISTORICAL_PURCHASE_CONVERSION");
            }
        }

        if (isUyu(currency) && disco.getCosto() != null) {
            return new AcquisitionCostResolution(
                    cost(disco.getCosto()), disco.getCosto(), currency, null, "HISTORICAL_CATALOG_UYU");
        }

        Optional<Pedido> purchase = purchaseFor(disco);
        BigDecimal rate = purchase.map(Pedido::getTipoCambio).map(this::positive).orElse(null);
        if (rate != null && disco.getCosto() != null) {
            return new AcquisitionCostResolution(
                    cost(disco.getCosto().multiply(rate)), disco.getCosto(), currency, rate,
                    "HISTORICAL_PURCHASE_CONVERSION");
        }
        return unavailableCost();
    }

    /** Calculates all sold lines in one sale, including any sale-level discount allocation. */
    public ProfitResult netProfitForSale(Venta venta) {
        if (venta == null || venta.getEstado() == EstadoVenta.CANCELADA) {
            return emptyResult();
        }

        List<DetalleVenta> details = venta.getDetalles() == null
                ? List.of()
                : venta.getDetalles().stream().filter(Objects::nonNull).toList();

        if (details.isEmpty()) {
            return calculateLegacySale(venta);
        }

        List<BigDecimal> actualAmounts = allocateActualLineAmounts(venta, details);
        List<ProfitItemResult> items = new ArrayList<>(details.size());
        for (int i = 0; i < details.size(); i++) {
            items.add(calculateItem(details.get(i), actualAmounts.get(i), venta));
        }
        return aggregate(items);
    }

    /** Calculates the net profit for all non-cancelled sales in a selected period. */
    public ProfitResult netProfitForPeriod(LocalDateTime desde, LocalDateTime hasta) {
        return netProfitForSales(ventaRepository.findAllForProfitPeriod(desde, hasta));
    }

    /** Useful for warnings in future consumers without requiring them to inspect result items. */
    public int missingAcquisitionCostCount(Venta venta) {
        return netProfitForSale(venta).affectedItemCount();
    }

    public boolean hasMissingAcquisitionCost(Venta venta) {
        return missingAcquisitionCostCount(venta) > 0;
    }

    /** Shared arithmetic primitive for sale-cost previews that already have a trusted cost. */
    public BigDecimal netProfitForAmounts(BigDecimal actualSaleAmount, BigDecimal acquisitionCost) {
        if (actualSaleAmount == null || acquisitionCost == null) return null;
        return money(actualSaleAmount.subtract(acquisitionCost));
    }

    private ProfitResult netProfitForSales(Collection<Venta> ventas) {
        List<ProfitItemResult> items = new ArrayList<>();
        if (ventas != null) {
            for (Venta venta : ventas) {
                ProfitResult saleResult = netProfitForSale(venta);
                items.addAll(saleResult.items());
            }
        }
        return aggregate(items);
    }

    private ProfitResult calculateLegacySale(Venta venta) {
        BigDecimal actualAmount = actualLegacySaleAmount(venta);
        AcquisitionCostResolution resolution = venta.getDisco() != null
                ? acquisitionCostForDisco(venta.getDisco()) : unavailableCost();
        BigDecimal acquisitionCost = resolution.isComplete()
                ? costMoney(resolution.unitCostUyu())
                : venta.getDisco() != null && isForeign(catalogCurrency(venta.getDisco()))
                    ? null
                    : positiveHistoricalCost(venta.getCostoDisco()) ? costMoney(venta.getCostoDisco()) : null;
        Long discoId = venta.getDisco() != null ? venta.getDisco().getIdDisco() : null;
        ProfitItemResult item = calculateItem(
                null,
                actualAmount,
                acquisitionCost,
                null,
                discoId,
                1,
                acquisitionCost == null ? "Missing historical sale acquisition cost" : null,
                acquisitionCost == null
                        ? unavailableCost()
                        : resolution.isComplete()
                            ? resolution
                            : new AcquisitionCostResolution(acquisitionCost, acquisitionCost, "UYU", null, "LEGACY_SALE_SNAPSHOT"));
        return aggregate(List.of(item));
    }

    private ProfitItemResult calculateItem(DetalleVenta detalle, BigDecimal actualAmount, Venta sale) {
        int quantity = quantityOf(detalle);
        AcquisitionCostResolution resolution = acquisitionCostForDetail(detalle);
        String missingReason = null;

        if (!resolution.isComplete()) {
            BigDecimal legacyCost = recoverSingleLineLegacyCost(sale, detalle);
            if (validHistoricalCost(legacyCost)) {
                resolution = new AcquisitionCostResolution(
                        cost(legacyCost), legacyCost, "UYU", null, "LEGACY_SALE_SNAPSHOT");
            }
        }
        if (!resolution.isComplete()) {
            missingReason = "Missing historical acquisition cost";
        }

        BigDecimal acquisitionTotal = !resolution.isComplete()
                ? null
                : costMoney(resolution.unitCostUyu().multiply(BigDecimal.valueOf(quantity)));
        return calculateItem(
                detalle,
                actualAmount,
                acquisitionTotal,
                detalle != null ? detalle.getIdDetalle() : null,
                detalle != null && detalle.getDisco() != null ? detalle.getDisco().getIdDisco() : null,
                quantity,
                missingReason,
                resolution);
    }

    private ProfitItemResult calculateItem(
            DetalleVenta detalle,
            BigDecimal actualAmount,
            BigDecimal acquisitionTotal,
            Long detailId,
            Long discoId,
            int quantity,
            String missingReason,
            AcquisitionCostResolution resolution) {
        BigDecimal saleAmount = money(actualAmount);
        if (acquisitionTotal == null) {
            return new ProfitItemResult(
                    detailId,
                    discoId,
                    quantity,
                    saleAmount,
                    null,
                    null,
                    ProfitStatus.UNAVAILABLE,
                    missingReason != null ? missingReason : "Missing historical acquisition cost",
                    null,
                    null,
                    null,
                    false);
        }

        BigDecimal netProfit = money(saleAmount.subtract(acquisitionTotal));
        return new ProfitItemResult(
                detailId,
                discoId,
                quantity,
                saleAmount,
                acquisitionTotal,
                netProfit,
                statusFor(netProfit),
                null,
                resolution.source(),
                resolution.originalCurrency(),
                resolution.exchangeRateUsed(),
                true);
    }

    private AcquisitionCostResolution acquisitionCostForDetail(DetalleVenta detail) {
        if (detail == null) return unavailableCost();
        if (validHistoricalCost(detail.getCostoAdquisicionUnitarioUyu())) {
            return new AcquisitionCostResolution(
                    cost(detail.getCostoAdquisicionUnitarioUyu()),
                    detail.getCostoAdquisicionUnitario(),
                    textOr(detail.getCostoAdquisicionMonedaOriginal(), "UYU"),
                    detail.getTipoCambioAdquisicion(),
                    textOr(detail.getCostoAdquisicionFuente(), "HISTORICAL_SALE_SNAPSHOT"));
        }

        // Rows created before the normalized snapshot was introduced may have
        // copied a foreign catalog amount into the old snapshot column.
        if (detail.getDisco() != null && isForeign(catalogCurrency(detail.getDisco()))) {
            AcquisitionCostResolution catalogResolution = acquisitionCostForDisco(detail.getDisco());
            if (catalogResolution.isComplete()) return catalogResolution;
            return unavailableCost();
        }
        if (validHistoricalCost(detail.getCostoAdquisicionUnitario())) {
            return new AcquisitionCostResolution(
                    cost(detail.getCostoAdquisicionUnitario()), detail.getCostoAdquisicionUnitario(),
                    textOr(detail.getCostoAdquisicionMonedaOriginal(), "UYU"),
                    detail.getTipoCambioAdquisicion(), "LEGACY_SALE_SNAPSHOT");
        }
        return unavailableCost();
    }

    private Optional<PedidoItem> purchaseItemFor(Disco disco) {
        if (pedidoItemRepository == null || disco.getIdDisco() == null) return Optional.empty();
        Optional<PedidoItem> linked = pedidoItemRepository
                .findFirstByDiscoIdDiscoOrderByIdPedidoItemDesc(disco.getIdDisco());
        if (linked.isPresent()) return linked;
        if (pedidoRepository == null || blank(disco.getNumeroFacturaCompra())) return Optional.empty();
        return pedidoRepository.findByNumeroFactura(disco.getNumeroFacturaCompra()).stream()
                .flatMap(pedido -> pedido.getItems().stream())
                .filter(item -> sameCatalogItem(item, disco))
                .findFirst();
    }

    private Optional<Pedido> purchaseFor(Disco disco) {
        if (pedidoRepository == null || blank(disco.getNumeroFacturaCompra())) return Optional.empty();
        return pedidoRepository.findByNumeroFactura(disco.getNumeroFacturaCompra()).stream().findFirst();
    }

    private boolean sameCatalogItem(PedidoItem item, Disco disco) {
        if (item.getDisco() != null && Objects.equals(item.getDisco().getIdDisco(), disco.getIdDisco())) return true;
        return item.getCodigo() != null && item.getCodigo().equalsIgnoreCase(disco.getCodigoInterno());
    }

    private String catalogCurrency(Disco disco) {
        if (!blank(disco.getCostoMoneda())) return disco.getCostoMoneda().trim().toUpperCase(Locale.ROOT);
        String source = disco.getProcedencia() == null ? "" : disco.getProcedencia().trim().toUpperCase(Locale.ROOT);
        return source.contains("FUTURE") ? "EUR" : "UYU";
    }

    private String purchaseCurrency(Pedido purchase, String fallback) {
        return purchase != null && !blank(purchase.getMoneda())
                ? purchase.getMoneda().trim().toUpperCase(Locale.ROOT) : fallback;
    }

    private boolean isForeign(String currency) { return !isUyu(currency); }
    private boolean isUyu(String currency) { return "UYU".equalsIgnoreCase(currency); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String textOr(String value, String fallback) { return blank(value) ? fallback : value; }
    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) { return first != null ? first : second; }
    private BigDecimal positive(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null; }
    private BigDecimal positiveOrZero(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) >= 0 ? value : null; }
    private AcquisitionCostResolution unavailableCost() { return new AcquisitionCostResolution(null, null, null, null, null); }

    private BigDecimal recoverSingleLineLegacyCost(Venta sale, DetalleVenta detail) {
        if (sale == null || detail == null || sale.getDetalles() == null
                || sale.getDetalles().size() != 1 || sale.getCostoDisco() == null
                || !validHistoricalCost(sale.getCostoDisco())) {
            return null;
        }
        if (detail.getDisco() != null && isForeign(catalogCurrency(detail.getDisco()))) {
            return null;
        }
        return costMoney(sale.getCostoDisco().divide(
                BigDecimal.valueOf(quantityOf(detail)), COST_SCALE, RoundingMode.HALF_UP));
    }

    private List<BigDecimal> allocateActualLineAmounts(Venta sale, List<DetalleVenta> details) {
        List<BigDecimal> originalAmounts = details.stream()
                .map(detail -> nvl(detail.getPrecioUnitario()).multiply(BigDecimal.valueOf(quantityOf(detail))))
                .toList();
        BigDecimal originalTotal = originalAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalTotal = finalSaleAmount(sale, originalTotal);

        if (originalAmounts.size() == 1) {
            return List.of(money(finalTotal));
        }

        BigDecimal baseTotal = originalAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (baseTotal.compareTo(BigDecimal.ZERO) == 0) {
            List<BigDecimal> allocated = new ArrayList<>(originalAmounts.size());
            for (int i = 0; i < originalAmounts.size() - 1; i++) allocated.add(ZERO);
            allocated.add(money(finalTotal));
            return allocated;
        }

        List<BigDecimal> allocated = new ArrayList<>(originalAmounts.size());
        BigDecimal allocatedBeforeLast = BigDecimal.ZERO;
        for (int i = 0; i < originalAmounts.size(); i++) {
            BigDecimal amount;
            if (i == originalAmounts.size() - 1) {
                amount = money(finalTotal.subtract(allocatedBeforeLast));
            } else {
                amount = money(finalTotal.multiply(originalAmounts.get(i))
                        .divide(baseTotal, 12, RoundingMode.HALF_UP));
                allocatedBeforeLast = allocatedBeforeLast.add(amount);
            }
            allocated.add(amount);
        }
        return allocated;
    }

    private BigDecimal finalSaleAmount(Venta sale, BigDecimal originalTotal) {
        if (sale != null && sale.getTotalFinal() != null) {
            return money(sale.getTotalFinal());
        }
        if (sale != null && sale.getTotal() != null) {
            return money(sale.getTotal());
        }
        BigDecimal discount = sale != null ? nvl(sale.getDescuentoPorcentaje()) : BigDecimal.ZERO;
        BigDecimal factor = ONE_HUNDRED.subtract(discount)
                .divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        return money(originalTotal.multiply(factor));
    }

    private BigDecimal actualLegacySaleAmount(Venta sale) {
        if (sale.getPrecioVenta() != null) return money(sale.getPrecioVenta());
        if (sale.getTotalFinal() != null) return money(sale.getTotalFinal());
        if (sale.getTotal() != null) return money(sale.getTotal());
        return ZERO;
    }

    private ProfitResult aggregate(Collection<ProfitItemResult> items) {
        List<ProfitItemResult> resultItems = items == null ? List.of() : List.copyOf(items);
        BigDecimal total = BigDecimal.ZERO;
        int unavailable = 0;
        boolean hasUnavailable = false;
        for (ProfitItemResult item : resultItems) {
            if (!item.isAvailable()) {
                unavailable++;
                hasUnavailable = true;
            } else {
                total = total.add(item.netProfit());
            }
        }
        BigDecimal netProfit = money(total);
        return new ProfitResult(
                netProfit,
                hasUnavailable ? ProfitStatus.UNAVAILABLE : statusFor(netProfit),
                unavailable,
                resultItems);
    }

    private ProfitResult emptyResult() {
        return new ProfitResult(ZERO, ProfitStatus.ZERO, 0, List.of());
    }

    private ProfitStatus statusFor(BigDecimal value) {
        int comparison = value.compareTo(BigDecimal.ZERO);
        if (comparison > 0) return ProfitStatus.POSITIVE;
        if (comparison < 0) return ProfitStatus.NEGATIVE;
        return ProfitStatus.ZERO;
    }

    private boolean validHistoricalCost(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    private boolean positiveHistoricalCost(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private int quantityOf(DetalleVenta detail) {
        return detail.getCantidad() != null && detail.getCantidad() > 0 ? detail.getCantidad() : 1;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return nvl(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal costMoney(BigDecimal value) {
        return value.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal cost(BigDecimal value) {
        return costMoney(value);
    }
}
