# Sonograma — Final Financial Audit After Phases 1–4

## 1. Executive Result

**PASS WITH DOCUMENTED RISKS**

The financial paths are internally consistent after Phases 1–4. The final regression matrix, cross-report reconciliation, fixed-clock boundary tests, frontend refresh tests, full backend suite, frontend suite, lint, and production build passed. No production code correction was required during this audit.

The remaining risks are limited to the existing browser-tab-only refresh model and a potential N+1 query pattern in the income calculator for large lists.

## 2. Phase 1 Verification

Sale-linked debt income remains split exactly once:

- `SALE` uses `montoPagadoInicial`.
- Each later valid `PagoDeuda` is a separate `PAGO_DEUDA` movement.
- Cumulative `Venta.montoPagado` is not reused as the sale movement amount.

The regression suite covers partial initial payment, later full payment, multiple partial payments, manual debt payments, and monthly aggregation without duplicate income.

## 3. Phase 2 Verification

Generated financial business dates use `BusinessTime`, backed by an injectable `Clock`, with `America/Montevideo` applied at the boundary. Active sale, debt, payment, reversal, expense, pre-sale, and financial-period paths use the centralized service when generating business dates.

User-supplied financial dates remain explicit input values. Non-financial timestamps in unrelated modules were not changed.

## 4. Phase 3 Verification

`FinancialMovementPolicy.isReportableDebtPayment` is the canonical rule:

```text
payment != null && anulado != true
```

Libro, Dashboard/statistics, monthly summary, debt balance calculations, and export input use this rule. Inactive debt or cancelled-sale state alone does not remove a valid historical `PagoDeuda` from income reporting.

## 5. Phase 4 Verification

Payment reversal is logical, not physical:

- The `PagoDeuda` row is retained.
- `anulado`, `fechaAnulacion`, and `anuladoPor` are stored.
- Original amount, payment date, creation timestamp, receipt number, idempotency key, and identity remain unchanged.
- Debt and linked `Venta` cached values are recalculated.
- Annulled payment income is excluded by the Phase 3 policy.
- The existing `sonograma:financial-data-changed` event refreshes mounted financial screens after payment, reversal, and debt mutations.

No new migration, negative synthetic movement, WebSocket, or cross-device synchronization system was introduced.

## 6. End-to-End Scenarios

| Scenario | Result |
|---|---|
| 1. Normal fully paid sale | PASS. Income is 1,000 on Libro, Dashboard, and monthly summary; no debt or duplicate movement. |
| 2. Sale with 400 initial payment and 600 debt | PASS. `SALE` is 400, income is 400, pending debt is 600. |
| 3. Later 600 debt payment | PASS. `SALE` 400 + `PAGO_DEUDA` 600 = 1,000; debt becomes fully paid. |
| 4. Initial 200 plus payments 300 and 200 | PASS. Income is 700, pending is 300, and movements are 200 + 300 + 200. |
| 5. Reverse payment A for 300 | PASS. Row remains stored and annulled; income becomes 400, paid is 400, pending is 600, payment B remains valid. |
| 6. Double reversal | PASS. Second attempt returns clear business validation and performs no second balance mutation. |
| 7. Manual debt payment and reversal | PASS. Payment is the only income movement; after reversal income is zero and the row remains stored. |
| 8. Cancelled sale with valid historical payment | PASS. The valid payment remains reportable; cancellation alone does not create a refund movement. |
| 9. Annul after sale cancellation | PASS. Payment is excluded everywhere while its historical row remains stored. |

Coverage is provided by `DeudaServiceTest`, `FinancialMovementPolicyTest`, `VentaServiceTest`, `EstadisticasServiceTest`, `ResumenFinancieroMensualServiceTest`, `FinancialReportingConsistencyTest`, `Deudas.test.jsx`, and `LibroVentas.test.jsx`.

## 7. Cross-Report Reconciliation

The representative fixed-date dataset contained:

| Dataset component | Included income |
|---|---:|
| Normal fully paid sale | 1,000 |
| Sale-linked initial payment | 400 |
| Later valid payment for that sale | 600 |
| Partially paid sale initial amount after reversing payment A | 200 |
| Valid payment B on that debt | 200 |
| Manual debt valid payment | 300 |
| Inactive debt valid payment | 150 |
| Cancelled-sale historical valid payment | 250 |
| Annulled payment A | 0 |
| Annulled manual payment | 0 |
| **Expected real income** | **3,100** |

For September 2026, the test asserted the exact same total:

- Libro de Ventas: **3,100**
- Dashboard income: **3,100**
- Monthly financial summary: **3,100**
- Libro export total: **3,100**

The export receives the already-filtered Libro movement list, so annulled rows cannot re-enter at export time.

## 8. Debt/Sale State Reconciliation

The debt calculation follows:

```text
montoPagadoInicial + SUM(valid non-annulled PagoDeuda)
```

with the existing total cap and positive-payment validation. `montoPendiente` is the capped total minus paid amount, never negative. For sale-linked debts, `Venta.montoPagado`, `Venta.montoDeuda`, and `Venta.estadoPago` are recalculated together.

Reversal tests verify partial recovery, full-payment reopening, inactive-debt handling, linked sale cache recovery, latest-valid-payment date, and repeated-reversal protection.

## 9. Timezone Boundary Verification

Backend fixed-clock tests passed with `-Duser.timezone=UTC`:

- Montevideo 2026-09-14 20:00 → business date 2026-09-14.
- Montevideo 2026-09-14 21:30 → business date 2026-09-14.
- Montevideo 2026-09-14 23:59:59 → business date 2026-09-14.
- Montevideo 2026-09-15 00:00 → business date 2026-09-15.
- Month boundary 2026-09-30/2026-10-01 → correct local dates.
- Year boundary 2026-12-31/2027-01-01 → correct local dates.

Frontend business-date tests passed with `TZ=UTC`, including UTC rollover, month boundary, and year boundary cases. Financial monthly grouping uses the stored business dates and passed the reconciliation tests.

## 10. Frontend Refresh Verification

After successful debt payment registration, Deudas updates its own state and dispatches the existing financial-change event. Dashboard listeners refresh income and recent movements; Libro listens to the same event. Debt save/update and payment reversal also notify through the same mechanism.

The Deudas listener ignores its own `source: 'deudas'` event, preventing a self-refresh loop. Libro reversal tests confirm the API call and event dispatch. The Dashboard key collision was confirmed and its movement key now uses movement type plus payment/sale identity; the helper has direct regression coverage.

WebSockets and cross-device synchronization were intentionally not added.

## 11. Historical Data Risks

No production data was read or mutated by this audit, and no repair query was executed. The following SELECT-only diagnostics are available for a controlled review before deployment:

```sql
-- Null or suspicious initial amounts
SELECT id_deuda, id_venta, monto_total, monto_pagado_inicial
FROM deuda
WHERE monto_pagado_inicial IS NULL
   OR monto_pagado_inicial < 0
   OR monto_pagado_inicial > monto_total;

-- Debt cached balance versus initial plus valid payment rows
SELECT d.id_deuda,
       d.monto_total,
       d.monto_pagado,
       d.monto_pendiente,
       LEAST(d.monto_total,
             COALESCE(d.monto_pagado_inicial, 0)
             + COALESCE(SUM(CASE WHEN p.anulado IS DISTINCT FROM TRUE THEN p.monto ELSE 0 END), 0)) AS expected_paid
FROM deuda d
LEFT JOIN pago_deuda p ON p.id_deuda = d.id_deuda
GROUP BY d.id_deuda, d.monto_total, d.monto_pagado, d.monto_pendiente, d.monto_pagado_inicial
HAVING d.monto_pagado <> LEAST(d.monto_total,
             COALESCE(d.monto_pagado_inicial, 0)
             + COALESCE(SUM(CASE WHEN p.anulado IS DISTINCT FROM TRUE THEN p.monto ELSE 0 END), 0));

-- Linked sale/debt cache mismatch
SELECT d.id_deuda, d.id_venta,
       d.monto_pagado AS deuda_pagado, v.monto_pagado AS venta_pagado,
       d.monto_pendiente AS deuda_pendiente, v.monto_deuda AS venta_deuda,
       d.estado_pago AS deuda_estado, v.estado_pago AS venta_estado
FROM deuda d
JOIN venta v ON v.id_venta = d.id_venta
WHERE d.monto_pagado <> v.monto_pagado
   OR d.monto_pendiente <> v.monto_deuda
   OR d.estado_pago <> v.estado_pago;

-- Annulled payments with their related cached state for review
SELECT p.id_pago_deuda, p.id_deuda, p.monto, p.fecha_pago,
       p.fecha_anulacion, p.anulado_por, d.monto_pagado, d.monto_pendiente
FROM pago_deuda p
JOIN deuda d ON d.id_deuda = p.id_deuda
WHERE p.anulado = TRUE;

-- Duplicate non-null idempotency keys within one debt
SELECT id_deuda, idempotency_key, COUNT(*) AS cantidad
FROM pago_deuda
WHERE idempotency_key IS NOT NULL
GROUP BY id_deuda, idempotency_key
HAVING COUNT(*) > 1;

-- Orphan payment rows (normally prevented by the foreign key)
SELECT p.id_pago_deuda, p.id_deuda
FROM pago_deuda p
LEFT JOIN deuda d ON d.id_deuda = p.id_deuda
WHERE d.id_deuda IS NULL;

-- Financial dates outside an agreed operational range
SELECT id_pago_deuda, id_deuda, fecha_pago
FROM pago_deuda
WHERE fecha_pago < DATE '2020-01-01'
   OR fecha_pago > DATE '2035-12-31';
```

Existing data may still contain legacy nulls or cache inconsistencies; this phase intentionally does not repair them.

## 12. Performance Risks

`IngresoLibroCalculator.montoVenta` performs a `DeudaRepository.findByVentaIdVenta` lookup for each sale-linked sale. Libro, Dashboard/statistics, and monthly-summary paths can therefore exhibit an N+1 pattern as the number of sales grows. The current tests show correct behavior, and no optimization was made because it was not necessary to prove correctness and could broaden the final-phase change surface.

## 13. Tests and Build Results

- Complete backend Maven suite: **384 tests, 0 failures, 0 errors, 1 skipped**.
- Targeted backend payment/reversal and controller tests: **19 passed**.
- Final cross-report reconciliation test: **2 passed**.
- Complete frontend suite: **136 tests passed across 20 test files**.
- Targeted frontend financial-refresh/key tests: **20 passed**.
- Backend timezone/policy/reconciliation run with `-Duser.timezone=UTC`: **5 tests passed**.
- Frontend business-date/key run with `TZ=UTC`: **3 tests passed**.
- Frontend lint: **passed**.
- Frontend production build: **passed**.
- `git diff --check`: **passed**.

## 14. Files Changed During Final Phase

No production code required correction during this final audit.

The only file added during the final audit was:

- `SONOGRAMA_FINAL_FINANCIAL_AUDIT.md`

The final regression coverage was expanded in the existing audit test files:

- `sonograma-backend/src/test/java/com/sonograma/service/FinancialReportingConsistencyTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/BusinessTimeTest.java`

Earlier Phase 1–4 files remain in the worktree as previously implemented and were not refactored in this phase.

## 15. Unrelated Modules

The following remained untouched during the final audit:

- Discogs
- VinylFuture
- imports
- catalogue
- pricing
- QR
- stock
- customers
- ecommerce
- authentication
- AWS deployment

## 16. Deployment Readiness

The code is **ready for a controlled production deployment**, subject to the normal release process and review of the SELECT-only historical diagnostics above. This audit did not deploy, repair data, change AWS Lightsail configuration, or change Docker deployment behavior.

The deployment should retain awareness of the documented N+1 scalability risk and the browser-tab-only refresh limitation.

## 17. Final Verification Checklist

- Can a debt payment be counted twice? **No.**
- Can a late-night Uruguay payment move to tomorrow because the server uses UTC? **No; business dates are derived through Montevideo `BusinessTime`.**
- Can Libro, Dashboard and monthly summary disagree because of different payment rules? **No; they use the same reportability policy and reconciled to 3,100 in the representative dataset.**
- Can an annulled payment appear as income? **No.**
- Is a reversed payment physically deleted? **No.**
- Can reversing twice corrupt balances? **No; the second reversal is rejected before mutation.**
- Do debt and linked sale balances reconcile? **Yes.**
- Do mounted financial screens refresh after payment/reversal? **Yes, through the existing financial-change event.**
- Did all backend tests pass? **Yes: 384 run, 0 failures, 0 errors, 1 skipped.**
- Did all frontend tests pass? **Yes: 136 passed.**
- Is the code ready for controlled deployment? **Yes, with the documented risks.**
