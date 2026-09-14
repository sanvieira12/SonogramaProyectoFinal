# Sonograma Phase 3 — Financial Reporting Consistency

## 1. Previous Problem

Libro de Ventas and Dashboard/statistics filtered `PagoDeuda` primarily by `anulado = false` in memory. The monthly summary instead used `PagoDeudaRepository.findValidosEntre`, whose query additionally required an active debt and a non-cancelled related sale.

As a result, a valid historical payment could appear in Libro and Dashboard but disappear from the monthly summary after debt deactivation or sale cancellation. The Libro export delegates to the Libro movement list, so it inherited whichever Libro policy was active.

The audit also confirmed that:

- `VentaService.cancelarVenta` marks the sale `CANCELADA` and the linked debt inactive.
- `PagoDeuda` has an explicit `anulado` reversal flag, plus annulment metadata.
- Payment deletion is a separate existing operation; there is no refund entity or explicit refund movement.
- Manual debts have no related sale, while sale-linked debts reference a `Venta`.
- No payment-cancellation/reversal migration was found or changed.

## 2. Canonical Payment Rule

`FinancialMovementPolicy.isReportableDebtPayment(...)` is now the single reusable definition.

A `PagoDeuda` counts as financial income if and only if it is not explicitly annulled: `anulado != true`.

Therefore:

1. Active debt + valid payment: counts.
2. Fully paid debt + valid payment: counts.
3. Inactive debt + historically valid payment: counts.
4. Cancelled sale + historically valid payment: counts.
5. `anulado = true`: does not count.
6. Manual debt payment: follows the same rule.
7. Sale-linked debt payment: follows the same rule.

Debt activity and sale state describe current domain state; they do not, by themselves, prove that historical cash was refunded.

## 3. Cancellation Behavior

- When a debt becomes inactive, existing non-annulled payments remain reportable.
- When a related sale is cancelled, existing non-annulled payments remain reportable because cancellation currently records state changes and stock restoration, not a refund movement.
- When a payment is annulled, it is excluded from all financial income reports.
- Physical payment deletion remains its existing behavior and was not redesigned in this phase.

This preserves real historical cash income unless the payment itself is explicitly reversed/annulled.

## 4. Files Changed

Production:

- `sonograma-backend/src/main/java/com/sonograma/service/FinancialMovementPolicy.java`
- `sonograma-backend/src/main/java/com/sonograma/repository/PagoDeudaRepository.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VentaService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/EstadisticasService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/ResumenFinancieroMensualService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/DeudaService.java`

Tests:

- `sonograma-backend/src/test/java/com/sonograma/service/FinancialMovementPolicyTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/FinancialReportingConsistencyTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DeudaServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/EstadisticasServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/ResumenFinancieroMensualServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/VentaServiceTest.java`

No frontend files were changed.

## 5. Reporting Paths

- Libro filters all payment rows through `FinancialMovementPolicy` before mapping them to `PAGO_DEUDA` movements.
- Dashboard/statistics uses the same policy in both catalog income aggregation and income-series aggregation.
- The legacy `ventas/estadisticas/por-mes` endpoint uses the same policy.
- Monthly summary reads payments by date range through `PagoDeudaRepository.findEntre` and applies the same policy in memory; the repository no longer applies active-debt or cancelled-sale filters.
- Libro export receives `ventaService.obtenerLibro(...)`, so it consumes the already-corrected movement list and has no separate validity predicate.
- Debt balance calculations also use the shared policy, keeping reportable and financially active payment handling aligned.

## 6. Phase 1 Compatibility

Phase 1 duplicate-counting protection remains intact.

For a sale linked to debt, the sale movement still uses the original `montoPagadoInicial`, while each valid later `PagoDeuda` is a separate movement. A later payment is not added again through the cumulative sale amount.

## 7. Phase 2 Compatibility

Phase 2 `BusinessTime` and injectable `Clock` implementation remain unchanged. Financial date generation continues to use `America/Montevideo`; this phase only changed payment inclusion policy and did not alter date formats or timezone behavior.

## 8. Tests Added or Updated

- `FinancialMovementPolicyTest`: active, fully paid, inactive, cancelled-sale-linked, annulled, manual, and sale-linked payment cases.
- `FinancialReportingConsistencyTest`: one shared population reconciled across Libro, Dashboard, and monthly summary.
- Libro regression: valid payment retained, annulled payment excluded, and export total reflects only the filtered movement list.
- Dashboard regression: valid historical payment retained for an inactive debt and annulled payment excluded.
- Monthly-summary regression: valid payment retained for an inactive debt tied to a cancelled sale, while an annulled payment is excluded.
- Existing debt-service tests continue to cover payment lifecycle and annulment behavior.

## 9. Cross-Report Reconciliation

The reconciliation fixture contains:

- Valid active payment: 100.
- Valid historical payment on an inactive debt linked to a cancelled sale: 200.
- Explicitly annulled payment: 900, excluded.

For September 2026, all three reporting surfaces produce the same income total:

| Surface | Expected income |
|---|---:|
| Libro | 300 |
| Dashboard/statistics | 300 |
| Monthly summary | 300 |

The Libro export is generated from the same filtered Libro movements.

## 10. Test Results

- Targeted Phase 3 backend suite: 49 tests passed, 0 failures, 0 errors.
- Full backend Maven suite: 382 tests executed, 0 failures, 0 errors, 1 skipped.
- `git diff --check`: passed.
- No frontend behavior changed, so no frontend test run was required for this phase.

## 11. Remaining Risks

- Frontend refresh/event propagation remains outside this phase and unresolved.
- Payment deletion versus annulment and the full audit-trail/refund model remain for the next phase.
- Existing historical data was not repaired or reinterpreted.
- Cancellation currently does not create an explicit refund movement; if that business meaning changes, the policy will need a dedicated reversal/refund model rather than inferring from sale state.

## 12. Final Verification

- Do Libro, Dashboard, and monthly summary use the same `PagoDeuda` validity rule? **Yes.**
- Can an annulled payment still appear as income? **No, not through the active financial reporting paths.**
- Can a valid historical payment disappear from only one report? **No, not because of debt inactivity or sale cancellation.**
- Are manual debts handled consistently? **Yes.**
- Is Phase 1 preserved? **Yes.**
- Is Phase 2 preserved? **Yes.**
- Were unrelated modules left untouched? **Yes; no frontend, Discogs, VinylFuture, catalog, pricing, stock, customer, authentication, deployment, or refresh/event changes were made.**
