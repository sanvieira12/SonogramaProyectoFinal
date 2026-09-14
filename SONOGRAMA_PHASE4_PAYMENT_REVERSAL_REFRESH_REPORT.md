# Sonograma Phase 4 — Payment Reversal and Financial Refresh

## 1. Previous Problems

The existing `DELETE` payment endpoint physically deleted `PagoDeuda`. That removed the historical payment row and required the debt and linked sale caches to be rebuilt from the remaining rows.

The debt screen refreshed itself after registering a payment, but did not consistently notify the Dashboard and other mounted financial consumers. This could leave totals and recent movements stale until a reload or remount.

## 2. New Reversal Model

Normal payment deletion is now a logical reversal. The existing DELETE routes remain in place, but the service now:

1. Locks and loads the payment and its related debt.
2. Rejects a payment that is already annulled with the existing clear business validation.
3. Sets `anulado = true`.
4. Sets `fechaAnulacion` using `BusinessTime` and records the authenticated username in `anuladoPor`.
5. Saves and flushes the same payment row.
6. Recalculates the debt and any linked sale cache.

No negative synthetic income movement is created.

## 3. Audit Trail

The original `PagoDeuda` row remains stored. Its amount, `fechaPago`, `createdAt`, receipt number, idempotency key, debt relationship, and identifier are preserved. Only the annulment fields are added or updated on the first reversal.

The existing entity fields and schema were sufficient; no migration was added.

## 4. Debt and Sale Recalculation

Recalculation continues to use the Phase 3 financial movement policy, so annulled payments are excluded while valid payments remain included.

Example:

| State | Paid | Pending |
|---|---:|---:|
| Sale total 1,000; initial payment 400; later payment 600 | 1,000 | 0 |
| After reversing the 600 payment | 400 | 600 |

For sale-linked debt, `Venta.montoPagado`, `Venta.montoDeuda`, and `Venta.estadoPago` are updated together with the debt. `fechaUltimoPago` also ignores annulled rows and falls back to the latest valid payment.

The same recalculation works for manual debt and for inactive debt rows whose historical payment is being reversed.

## 5. Financial Reporting

All affected reporting paths continue to use the Phase 3 rule: a debt payment is reportable when `anulado != true`.

The policy is applied consistently to Libro, Dashboard/statistics, monthly summary, debt balances, and the filtered movement list consumed by export. Debt inactivity or linked sale cancellation alone does not erase historical cash income.

## 6. Frontend Refresh

The existing `sonograma:financial-data-changed` event is reused.

After successful payment registration, payment reversal, and debt create/update, the frontend dispatches the event. Dashboard listeners refresh inventory, Libro movements/summary, and the income series. Libro already emitted the same event after payment reversal and continues to do so.

The Deudas listener ignores events marked with `source: 'deudas'`, preventing a self-refresh loop while other mounted financial surfaces still refresh.

The Dashboard movement key collision was confirmed: its source list mixes `VENTA` and `PAGO_DEUDA` rows, and payment rows can have a null or repeated `idVenta`. It now uses a composite type-plus-movement identifier. The existing Libro key was already composite and was left unchanged.

## 7. API Compatibility

The existing routes and request payloads are unchanged:

- `DELETE /deudas/pagos/{idPagoDeuda}`
- `DELETE /deudas/{idDeuda}/pagos/{idPagoDeuda}`

Their external response remains `204 No Content`. Internally, the operation now means logical annulment rather than physical deletion. The authenticated principal is used only to populate the existing audit field.

## 8. Files Changed

Production files changed for Phase 4:

- `sonograma-backend/src/main/java/com/sonograma/service/DeudaService.java`
- `sonograma-backend/src/main/java/com/sonograma/controller/DeudaController.java`
- `frontend/src/pages/Deudas.jsx`
- `frontend/src/pages/Dashboard.jsx`
- `frontend/src/utils/financialMovementKey.js`

Regression tests changed or added:

- `sonograma-backend/src/test/java/com/sonograma/service/DeudaServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/controller/DeudaControllerTest.java`
- `frontend/src/pages/Deudas.test.jsx`
- `frontend/src/pages/LibroVentas.test.jsx`
- `frontend/src/utils/financialMovementKey.test.js`

No Discogs, VinylFuture, catalogue, pricing, QR, stock, customer, ecommerce, authentication, deployment, or timezone-architecture changes were made for this phase.

## 9. Tests

Regression coverage includes:

1. Reversing a valid payment retains the row, sets audit fields, recalculates paid/pending values, and performs no repository delete.
2. Reversing a partial payment leaves other valid payments counted.
3. Reversing an already-annulled payment returns a clear business validation without saving or recalculating again.
4. Reversing a payment on an inactive/manual debt recalculates the debt and retains the row.
5. Sale-linked debt reversal reconciles the linked `Venta` cache.
6. Libro, Dashboard/statistics, and monthly summary exclude annulled payments while retaining valid historical payments.
7. Successful frontend payment registration dispatches the shared financial refresh event.
8. Successful frontend payment reversal dispatches the same event.

## 10. Test Results

- Targeted backend payment/controller tests: 19 tests passed, 0 failures, 0 errors.
- Targeted frontend payment/key tests: 20 tests passed, 0 failures.
- Complete backend suite: 383 tests, 0 failures, 0 errors, 1 skipped.
- Complete frontend suite: 136 tests passed across 20 test files.
- Frontend lint: passed.
- Frontend production build: passed.

## 11. Phase Compatibility

- Phase 1 preserved: sale-linked initial payment remains represented once by the sale movement; later valid debt payments remain separate `PAGO_DEUDA` movements.
- Phase 2 preserved: reversal timestamps and all existing financial dates use `BusinessTime` and `America/Montevideo` through the injectable clock.
- Phase 3 preserved: `FinancialMovementPolicy` remains the single reportability rule, and inactive debts/cancelled sales do not independently erase historical cash.

## 12. Remaining Risks

The event mechanism is browser-tab local. Cross-tab or cross-device synchronization is not implemented, as required by scope; a reload or existing navigation remains necessary in another tab.

## 13. Final Verification

- Is a reversed payment still physically deleted? **No.**
- Does the historical payment remain stored? **Yes.**
- Can an annulled payment still affect debt balance? **No; it is excluded by the Phase 3 policy.**
- Can an annulled payment still appear as income? **No; all affected reporting paths exclude it.**
- Can reversing twice subtract twice? **No; the second reversal returns a clear business validation before mutation.**
- Does linked Venta state recalculate? **Yes.**
- Does a successful payment notify other financial screens? **Yes, through `sonograma:financial-data-changed`.**
- Does reversing a payment notify them? **Yes, through the same event.**
- Were unrelated modules left untouched? **Yes; no unrelated modules, migration, data repair, or deployment were performed.**
