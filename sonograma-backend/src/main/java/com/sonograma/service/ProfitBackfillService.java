package com.sonograma.service;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.VentaRepository;
import com.sonograma.enums.EstadoVenta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

/**
 * Explicitly controlled historical-data backfill. The default operation is a
 * dry run; execution only fills missing normalized cost snapshots and never
 * changes a recorded sale price or an existing valid snapshot.
 */
@Service
@RequiredArgsConstructor
public class ProfitBackfillService {
    private final DetalleVentaRepository detalleVentaRepository;
    private final VentaRepository ventaRepository;
    private final ProfitCalculationService profitCalculationService;

    @Transactional
    public ProfitBackfillReport run(boolean execute) {
        List<DetalleVenta> details = detalleVentaRepository.findAll();
        int reliable = 0;
        int fallback = 0;
        int unavailable = 0;
        int updated = 0;
        List<ProfitBackfillReport.Sample> samples = new ArrayList<>();

        for (DetalleVenta detail : details) {
            ProfitItemResult before = profitCalculationService.netProfitForSoldItem(detail);
            AcquisitionCostResolution resolution = profitCalculationService.resolveHistoricalCostForBackfill(detail);
            boolean hasSnapshot = detail.getCostoAdquisicionUnitarioUyu() != null
                    && detail.getCostoAdquisicionFuente() != null;
            boolean isFallback = resolution.isComplete() && resolution.source() != null
                    && resolution.source().startsWith("CURRENT_STOCK_");
            if (!resolution.isComplete()) unavailable++;
            else if (isFallback) fallback++;
            else reliable++;

            boolean canFill = !hasSnapshot && resolution.isComplete();
            if (execute && canFill) {
                detail.setCostoAdquisicionUnitarioUyu(resolution.unitCostUyu());
                detail.setCostoAdquisicionUnitario(resolution.originalAmount());
                detail.setCostoAdquisicionMonedaOriginal(resolution.originalCurrency());
                detail.setTipoCambioAdquisicion(resolution.exchangeRateUsed());
                detail.setCostoAdquisicionFuente(resolution.source());
                detalleVentaRepository.save(detail);
                updated++;
            }

            // Keep the legacy persisted sale total in sync for dashboard/export
            // consumers, while retaining cancellation audit values untouched.
            if (execute && detail.getVenta() != null
                    && detail.getVenta().getEstado() != EstadoVenta.CANCELADA) {
                ProfitResult saleProfit = profitCalculationService.netProfitForSale(detail.getVenta());
                detail.getVenta().setGananciaEstimada(saleProfit.isAvailable() ? saleProfit.grossProfit() : null);
                ventaRepository.save(detail.getVenta());
            }

            if (samples.size() < 10 && (canFill || !before.isAvailable())) {
                BigDecimal after = resolution.isComplete() && detail.getPrecioUnitario() != null
                        ? profitCalculationService.netProfitForAmounts(
                                detail.getPrecioUnitario().multiply(java.math.BigDecimal.valueOf(
                                        detail.getCantidad() == null || detail.getCantidad() < 1 ? 1 : detail.getCantidad())),
                                resolution.unitCostUyu().multiply(java.math.BigDecimal.valueOf(
                                        detail.getCantidad() == null || detail.getCantidad() < 1 ? 1 : detail.getCantidad())))
                        : null;
                samples.add(new ProfitBackfillReport.Sample(detail.getIdDetalle(), before.netProfit(), after,
                        resolution.source(), resolution.isComplete() ? "AVAILABLE" : "UNAVAILABLE"));
            }
        }
        return new ProfitBackfillReport(execute, details.size(), reliable, fallback, unavailable, updated, samples);
    }
}
