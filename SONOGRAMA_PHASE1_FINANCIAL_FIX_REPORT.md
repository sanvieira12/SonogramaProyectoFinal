# Sonograma Phase 1 — Financial Duplicate Counting Fix

Audit/fix date: 2026-09-14  
Repository commit before changes: `f50e6ab`  
Scope: duplicate income counting only. No production data was changed.

## 1. Root Cause

`DeudaService.recalcularEstado` keeps `Venta.montoPagado` as the cumulative amount paid:

```text
initial payment + valid PagoDeuda payments
```

At the same time, each later payment is stored as its own `PagoDeuda` movement. The previous shared `IngresoLibroCalculator.montoVenta` used cumulative `Venta.montoPagado` for the sale movement, while Libro, Dashboard statistics, and the monthly summary also added the later `PagoDeuda` rows.

For a sale of 1,000 paid initially at 400, followed by a 600 debt payment, the old reporting result could be:

```text
sale movement:          1,000  (cumulative Venta.montoPagado)
debt payment movement:    600  (PagoDeuda)
reported total:         1,600
```

The same 600 was therefore represented once inside the cumulative sale amount and again as a payment movement.

## 2. Solution

The reporting rule is now:

```text
sale movement = amount paid when the sale/debt movement was created
               = Deuda.montoPagadoInicial for sale-linked debt

debt payment movements = each valid PagoDeuda row
```

`IngresoLibroCalculator` now resolves the sale-linked `Deuda` by `Venta.idVenta` and uses `Deuda.montoPagadoInicial` for the sale movement. It deliberately does not change `Venta.montoPagado` or debt recalculation.

For sales without a debt row, the existing `Venta.montoPagado` fallback remains in place. This preserves the full paid amount for fully paid sales created without later debt payments, including the normal no-debt sale case.

Manual debts have no `Venta`, so they still produce only their valid `PagoDeuda` movements and no synthetic sale income.

The monthly summary and the legacy `/ventas/estadisticas/por-mes` aggregation were changed to use the same shared calculator for sale income. Libro already used that calculator for its sale DTOs, Dashboard already used it for income statistics, and the current Libro export already consumes those corrected movement DTOs.

No database column, migration, timezone setting, cancellation rule, or frontend behavior was changed.

## 3. Files Changed

### Production code

- `sonograma-backend/src/main/java/com/sonograma/service/IngresoLibroCalculator.java`
  - Injects `DeudaRepository`.
  - Uses `Deuda.montoPagadoInicial` for sale-linked sale movements.
  - Keeps the existing fallback for sales without a debt.

- `sonograma-backend/src/main/java/com/sonograma/service/ResumenFinancieroMensualService.java`
  - Routes sale-date income through `IngresoLibroCalculator` instead of directly reading cumulative `Venta.montoPagado`.

- `sonograma-backend/src/main/java/com/sonograma/service/VentaService.java`
  - Routes the legacy `/ventas/estadisticas/por-mes` sale aggregation through `IngresoLibroCalculator` instead of manually subtracting payment history from cumulative paid.

### Tests

- `sonograma-backend/src/test/java/com/sonograma/service/VentaServiceTest.java`
  - Updated a sale/payment fixture to represent cumulative sale paid versus original initial paid.
  - Added partial-sale/no-later-payment coverage.
  - Added manual-debt-only-payment coverage.

- `sonograma-backend/src/test/java/com/sonograma/service/EstadisticasServiceTest.java`
  - Updated Dashboard fixtures to use coherent initial-plus-later payment amounts.
  - Added assertions that the sale movement uses the initial amount and that totals reconcile without duplication.

- `sonograma-backend/src/test/java/com/sonograma/service/ResumenFinancieroMensualServiceTest.java`
  - Added monthly-summary regression coverage for a fully settled debt-linked sale.
  - Added partial-payment coverage that checks both income and remaining debt.

`ExcelExportService.java` was not modified because the active export endpoint calls `VentaService.obtenerLibro` and passes its already-correct `VentaResponseDTO` movement list to `exportarLibroMovimientos`.

## 4. Reporting Behavior Before vs After

Scenario:

```text
sale total:       1,000
initial payment:    400
debt:               600
later payment:      600
```

### Before

```text
SALE:         1,000  (cumulative Venta.montoPagado)
PAGO_DEUDA:     600
TOTAL:        1,600
```

### After

```text
SALE:           400  (Deuda.montoPagadoInicial)
PAGO_DEUDA:     600  (valid PagoDeuda)
TOTAL:        1,000
```

The same rule is used by:

- Libro de Ventas (`VentaService.obtenerLibro` → `IngresoLibroCalculator`);
- Dashboard income statistics (`EstadisticasService` → `IngresoLibroCalculator`);
- monthly financial summary (`ResumenFinancieroMensualService` → `IngresoLibroCalculator`);
- current Libro export (`VentaService.obtenerLibro` → `ExcelExportService.exportarLibroMovimientos`).

## 5. Impact on Debt Logic

Debt balance logic remains cumulative and was not changed.

`DeudaService.calcularBalance` still computes:

```text
paid    = min(montoPagadoInicial + valid positive PagoDeuda amounts, montoTotal)
pending = max(montoTotal - paid, 0)
```

`DeudaService.recalcularEstado` still updates:

- `Deuda.montoPagado`;
- `Deuda.montoPendiente`;
- `Deuda.estadoPago`;
- linked `Venta.montoPagado`;
- linked `Venta.montoDeuda`;
- linked `Venta.estadoPago`.

Payment validation, remaining debt, fully paid state, customer debt display, locking, and idempotency are unchanged.

Only the reporting interpretation of a sale-linked sale row changed from cumulative paid to original paid-at-creation.

## 6. Existing Data Compatibility

The fix uses the existing `deuda.monto_pagado_inicial` field. Migration `docs/migraciones/029_deudas_balance_por_movimiento.sql` added that field and backfilled it by subtracting positive payment history from the cached paid amount, capped to the debt total. This is the existing field specifically intended to preserve the original amount paid when the debt movement was created.

Interpretation rules for existing records:

- Sale with a linked debt and a populated `montoPagadoInicial`: use that initial amount for the sale movement.
- Sale without a linked debt: retain the existing `Venta.montoPagado`/total fallback. This covers normal fully paid sales with no later debt movement.
- Manual debt without a sale: no sale movement is generated; valid payment rows remain the income movements.
- Linked debt whose `montoPagadoInicial` is null: the calculator returns zero rather than inventing an amount. The entity/schema expects this field to be non-null, so such a row is a data-quality anomaly requiring separate review.
- Linked debt whose stored initial value was historically incorrect: this code cannot infer the intended business amount with certainty. No production data was repaired and no amount was invented.

The implementation does not mutate or normalize historical records. A read-only production reconciliation should identify null/zero initial values, cached-balance mismatches, and payment-history anomalies before any future data repair phase.

## 7. Tests Added or Updated

The regression coverage now includes the requested scenarios:

1. Fully paid normal sale with no debt movement: full paid amount remains income.
2. Partial sale with no later payment: only the initial payment is the sale movement.
3. Partial sale followed by full debt payment: initial sale amount plus payment equals the sale total, never the cumulative sale paid plus payment.
4. Multiple debt payments: initial sale amount plus all valid payment rows equals the total cash received.
5. Partial debt still pending: income equals initial plus later payment and pending debt remains correct.
6. Manual debt: only payment movements are reported; no synthetic sale is created.

The strongest assertions are in:

- `VentaServiceTest.obtenerLibroIncluyePagosDeDeudaComoIngresoSeparado`;
- `VentaServiceTest.obtenerLibroUsaPagoInicialEnVentaParcialSinCobrosPosteriores`;
- `VentaServiceTest.obtenerLibroManualDebtSoloIncluyePagosSinVentaSintetica`;
- `EstadisticasServiceTest.dashboardReplicaLibroConVentaYPagoDeDeudaComoMovimientosSeparados`;
- `EstadisticasServiceTest.dashboardCuentaPagosParcialesYCompletosComoTransaccionesIndependientes`;
- `ResumenFinancieroMensualServiceTest.noDuplicaVentaAcumuladaConPagoPosterior`;
- `ResumenFinancieroMensualServiceTest.mantieneIngresoRealYDeudaPendienteEnPagoParcial`.
- `VentaServiceTest.estadisticasPorMesUsaPagoInicialYNoDuplicaPagoPosterior`.

The monthly summary regression is equivalent to the reconciliation assertion:

```text
initial sale cash movements
+ valid debt payment movements
= real cash received
```

## 8. Test Results

### Targeted backend tests

Command:

```text
mvn -q -Dtest=VentaServiceTest,EstadisticasServiceTest,ResumenFinancieroMensualServiceTest,ExcelExportServiceTest test
```

Result:

```text
Tests run: 29
Failures: 0
Errors: 0
Skipped: 0
```

Breakdown:

- `VentaServiceTest`: 10 passed;
- `EstadisticasServiceTest`: 12 passed;
- `ResumenFinancieroMensualServiceTest`: 5 passed;
- `ExcelExportServiceTest`: 2 passed.

### Complete backend suite

Command:

```text
mvn -q test
```

Result:

```text
Tests run: 375
Failures: 0
Errors: 0
Skipped: 1
```

Frontend tests were not run because no frontend code was affected in this phase.

## 9. Remaining Risks

Only out-of-scope or unresolved risks are listed here:

- Timezone handling remains unchanged. Default `LocalDate.now()` / `LocalDateTime.now()` behavior and production runtime timezone must be addressed in the next phase.
- Cancellation consistency remains unchanged. Libro/Dashboard versus monthly-summary inclusion of payments tied to inactive/cancelled records still requires a separate business decision and phase.
- Frontend refresh behavior remains unchanged. A successful debt payment may not dispatch the global financial-change event to other mounted surfaces.
- Historical records with missing or incorrect `montoPagadoInicial` remain ambiguous; this phase deliberately does not invent or repair amounts.
- `IngresoLibroCalculator` performs a debt lookup for sale movements. This is correct and narrow for this phase but may be optimized separately if reporting volume makes the lookup cost material.
- Legacy callers of the unused/older `ExcelExportService.exportarLibroVentas(List<Venta>)` method are outside the active movement export path and were not changed in this phase.
- Cancellation behavior, migration governance, schema drift, and unrelated modules were intentionally not addressed.

## 10. Final Verification

### Can the same debt payment still be counted twice?

Not through the corrected reporting paths: the sale row uses only `Deuda.montoPagadoInicial`, and each valid `PagoDeuda` is added separately. The cumulative `Venta.montoPagado` is no longer used as the sale movement when a linked debt exists.

### Does Libro use the original sale payment?

Yes. Sale-linked Libro rows use `Deuda.montoPagadoInicial`; later valid payments remain `PAGO_DEUDA` rows.

### Does Dashboard use the same rule?

Yes. Dashboard income aggregation uses the same `IngresoLibroCalculator`.

### Does monthly summary use the same rule?

Yes. Sale-date income now uses the same calculator, while its existing payment validity/cancellation filters remain unchanged for this phase.

### Are debt balances still correct?

Yes by code path and passing regression/full backend tests. Cumulative debt balance calculation, pending amount, paid state, payment validation, locking, and idempotency were not changed.

### Were unrelated modules left untouched?

Yes. No Discogs, VinylFuture, imports, catalogue, pricing, QR, customers, stock rules, receipts, ecommerce, AWS deployment, authentication, timezone configuration, cancellation rules, or frontend layout files were modified.

Phase 1 is complete. Timezone, cancellation consistency, and frontend refresh remain for later phases.
