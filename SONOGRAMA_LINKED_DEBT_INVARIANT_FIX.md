# Sonograma Linked Debt Invariant Fix

## 1. Root Cause

`PUT /deudas/{idDeuda}` used the same update request path for manual debts and debts linked to sales. `DeudaService.actualizar(...)` accepted `montoTotal` and recalculated the debt balance and linked sale payment caches, but did not prevent a linked debt total from diverging from the authoritative `Venta.totalFinal`.

## 2. Existing Behavior

The Deudas frontend exposed `montoTotal` as an editable numeric field for every debt. The backend request DTO also allowed `montoTotal` for every debt. Sale creation and sale editing already called `DeudaService.sincronizarVenta(...)`, which propagated the sale total to its linked debt, but the independent debt-edit endpoint could bypass that ownership rule.

Manual debts and sale-linked debts therefore shared the update contract even though their totals have different ownership semantics.

## 3. Implemented Invariant

For a debt with `deuda.getVenta() != null`:

```text
Deuda.montoTotal == Venta.totalFinal
```

`DeudaService.actualizar(...)` now rejects a request whose `montoTotal` differs from the linked sale total. It also rejects an already-divergent linked debt before any recalculation or save, so the attempted update cannot silently repair or further mutate either object.

The canonical comparison uses `Venta.totalFinal`, with the existing `Venta.total` value as a null fallback. A missing sale total is rejected with a clear business validation error.

## 4. Backend Changes

- Added linked-total validation before debt recalculation and persistence in `DeudaService.actualizar(...)`.
- Kept the rejection before `recalcularEstado(...)`, preventing linked sale cache mutation on a rejected edit.
- Left `montoPagadoInicial + valid non-annulled PagoDeuda` balance calculation and the cap at `montoTotal` intact.
- Preserved the existing payment path, where linked payment changes synchronize `Venta.montoPagado`, `Venta.montoDeuda`, and `Venta.estadoPago`.
- Did not add any migration or production-data repair.

## 5. Frontend Changes

In `frontend/src/pages/Deudas.jsx`, the total field is now read-only when the selected debt movement has `idVenta != null`. The UI explains:

> El total proviene de la venta vinculada. Editá la venta para cambiarlo.

Manual debt totals remain editable. No unrelated UI was redesigned.

## 6. Manual Debt Compatibility

Manual debts (`idVenta == null`) continue through the existing update path. Their `montoTotal` may be edited, and the pending balance is recalculated from the updated total and the valid payment balance.

Regression coverage confirms a manual debt changing from `1000` to `1500` remains `PENDIENTE` with `montoPagado = 0` and `montoPendiente = 1500`.

## 7. Linked Sale Compatibility

The sale remains authoritative for the original transaction total. Legitimate sale synchronization continues through `DeudaService.sincronizarVenta(...)`; changing a sale total to `2300` updates the linked debt total to `2300` and recalculates its pending amount.

Debt payment and reversal paths continue to synchronize only the sale payment caches (`montoPagado`, `montoDeuda`, `estadoPago`). They do not alter `Venta.total`, `Venta.totalFinal`, item prices, or discounts.

## 8. Regression Tests

The following coverage is present and passing:

1. `deudaVinculadaRechazaUnTotalDistintoAlTotalDeLaVentaSinMutar`: sale total `1900`, linked debt total `1900`, attempted debt total `2300`; request rejected, no debt save, and both totals remain unchanged.
2. `deudaManualPuedeCambiarMontoTotalYRecalculaPendiente`: manual debt total `1000` to `1500`; pending recalculates correctly.
3. `pagoDeDeudaVinculadaSincronizaLosCachesDeLaVenta`: valid linked payment updates debt and sale payment caches coherently.
4. `eliminarPagoRestauraSaldoEstadoFechaYVentaVinculada`: payment row is retained and marked annulled; it is excluded from valid totals and debt/sale balances recalculate.
5. `edicionDeVentaSincronizaElNuevoTotalConLaDeudaVinculada`: the legitimate sale synchronization path updates the linked debt total.
6. `Deudas.test.jsx`: linked debt total is rendered read-only with the sale-authority explanation; existing manual/debt-payment behavior remains covered.

## 9. Full Test Results

- Targeted backend debt/sale: `mvn -q -Dtest=DeudaServiceTest,VentaServiceTest test` — 30 tests passed, 0 failures, 0 errors.
- Full backend: `mvn -q test` — 388 tests, 0 failures, 0 errors, 1 skipped.
- Targeted frontend: `npm test -- --run src/pages/Deudas.test.jsx` — 6 tests passed.
- Full frontend: `npm test -- --run` — 20 test files, 138 tests passed.
- Frontend lint: `npm run lint` — passed.
- Production build: `npm run build` — passed.

## 10. Files Changed

This invariant-fix phase changed the following application files in the existing worktree:

- `sonograma-backend/src/main/java/com/sonograma/service/DeudaService.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DeudaServiceTest.java`
- `frontend/src/pages/Deudas.jsx`
- `frontend/src/pages/Deudas.test.jsx`

The worktree already contained changes and reports from earlier Sonograma phases; those were preserved and not reverted.

## 11. Remaining Historical Production Records

No production connection, SSH command, migration, deployment, restart, or database write was performed for this task.

The following historical records remain untouched for separate evaluation, as required:

- debt `30` / sale `25`
- debt `53` / sale `34`
- debt `67` / sale `48`
- debt `89` / sale `76`
- debt `102` / sale `90`

## 12. Final Result

The code now enforces sale ownership of linked debt totals at the backend boundary and communicates the same rule in the frontend. Manual debt editing, payment synchronization, payment reversal, and legitimate sale editing remain supported.

PASS — LINKED DEBT TOTAL INVARIANT ENFORCED
