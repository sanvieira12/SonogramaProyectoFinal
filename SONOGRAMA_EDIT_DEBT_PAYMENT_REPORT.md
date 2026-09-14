# Sonograma — Editing Existing Debt Payments

## 1. Existing Architecture

Libro de Ventas already represents persisted debt payments as `PAGO_DEUDA` movements with the stable `idPagoDeuda`. Existing creation and logical reversal use `DeudaService`, `PagoDeudaRepository`, and the shared financial movement policy. `PagoDeuda` stores `monto`, `fechaPago` as `LocalDate`, `numeroRecibo`, `notas`, idempotency data, and reversal/audit fields. It has no payment-method field, so no method selector was added.

## 2. Files Changed

Implemented only the debt-payment editing flow and its tests:

- `sonograma-backend/src/main/java/com/sonograma/dto/PagoDeudaUpdateRequest.java`
- `sonograma-backend/src/main/java/com/sonograma/controller/DeudaController.java`
- `sonograma-backend/src/main/java/com/sonograma/service/DeudaService.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DeudaServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/controller/DeudaControllerTest.java`
- `frontend/src/api/sonograma.js`
- `frontend/src/api/sonograma.test.js`
- `frontend/src/pages/LibroVentas.jsx`
- `frontend/src/pages/LibroVentas.test.jsx`

Pre-existing unrelated dirty-worktree files were not modified, staged, or removed.

## 3. Backend Update Flow

Added authenticated `PUT /deudas/pagos/{idPagoDeuda}`. The service locks the existing payment and related debt, rejects missing/non-positive amounts, missing dates, missing payments, and annulled payments, then validates the resulting payment sum before changing anything. A successful edit updates the same payment row and saves the recalculated debt state.

The payment ID, creation timestamp, idempotency key, and reversal/audit fields remain unchanged. No delete, replacement row, negative compensation, or new financial movement is created.

## 4. Editable Fields

The drawer exposes only:

- amount;
- payment date as a `YYYY-MM-DD` `LocalDate`;
- payment receipt/boleta number;
- payment notes.

Derived debt, sale, status, and total fields are not editable. A payment method was not exposed because `PagoDeuda` has no such persisted field.

## 5. Financial Recalculation

After a valid update, the service recalculates the debt’s paid amount, pending amount, payment status, and last-payment date from the reportable payments. For a linked sale it updates only `montoPagado`, `montoDeuda`, and `estadoPago` caches. `Venta.totalFinal` and `Deuda.montoTotal` are not changed by payment editing.

## 6. Historical Divergence Protection

The regression fixture preserves the intentionally divergent values `Venta.totalFinal = 1390` and `Deuda.montoTotal = 790`. Editing its payment from 300 to 200 leaves both totals unchanged while recalculating the debt and sale caches to paid 200, pending 590, and `PARCIAL`. The existing direct debt-total edit rejection and authoritative sale-edit synchronization tests remain present and passing.

## 7. Frontend Behavior

The existing Libro de Ventas drawer now shows `Editar` and `Anular pago` for a payment movement. `Editar` opens the payment form in the same drawer. Saving calls the new update API, reloads the Libro de Ventas movement list and monthly summary, keeps the stable movement identity, emits `sonograma:financial-data-changed`, and shows a success message. Existing sale editing/cancellation and pre-sale payment behavior remain unchanged.

## 8. Tests

Added or extended coverage for:

- reducing an existing payment amount;
- increasing an existing payment amount without creating another movement;
- rejecting an excessive amount without partial mutation;
- rejecting an annulled payment;
- preserving a historical divergent linked sale/debt total;
- controller delegation for the update endpoint;
- frontend drawer editing, payload submission, refresh, event emission, and retained annul action;
- frontend API `PUT` request shape.

## 9. Validation Results

- Targeted backend: `DeudaServiceTest` 27 tests, 0 failures, 0 errors; `DeudaControllerTest` 4 tests, 0 failures, 0 errors.
- Complete backend suite: 396 tests, 0 failures, 0 errors, 1 skipped.
- Complete frontend suite: 140 tests passed.
- Frontend lint: passed.
- Frontend production build: passed.
- `git diff --check`: passed.

## 10. Deployment Impact

No production server was contacted. No production deployment, migration, restart, database write, or production data change occurred. The backend endpoint and frontend bundle require the normal future application deployment to become available; this task does not authorize or perform that deployment.

PASS — DEBT PAYMENT EDITING IMPLEMENTED AND VERIFIED
