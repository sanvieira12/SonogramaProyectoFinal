# Sales Book Refresh Change Report

## 1. Root cause

The Sales Book loaded two independent datasets:

- the movement table through `api.libro.listar(...)`;
- the KPI summary through `api.ventas.resumenMensual(...)`.

Successful sale cancellation and debt-payment annulment called only the table loader, so the movement disappeared from the table while the KPI cards retained the previous backend summary.

Other financial mutations did request both datasets, but they refreshed the summary with the editable `periodo` state while refreshing the table with the last `applied` range. If a user changed the month input without applying it, a subsequent mutation could request the table for the applied month and the summary for the unapplied month.

## 2. Previous request flow

On initial render, `LibroVentas` independently called:

- `cargar({ desde: primerDiaMes, hasta: hoy })` for movements;
- `cargarResumen(periodoActual)` for KPI cards.

Applying filters built a range from editable `periodo`, stored it in `applied`, then independently loaded the table with that range and the summary with `periodo`. Clearing filters reset and loaded both current-month datasets.

Before this fix, post-mutation behavior was inconsistent:

- sale cancellation and debt-payment annulment called `cargar(applied)` but did not call `cargarResumen`;
- pre-sale payment edit/deletion and debt-payment edit called `cargar(applied)` and `cargarResumen(periodo)`;
- sale edit updated the table locally from the mutation response and called `cargarResumen(periodo)`, without re-requesting the authoritative table data.

## 3. Mutations audited

| Sales Book action | Can affect KPI summary? | Reason |
|---|---:|---|
| Edit normal sale | Yes | Can affect sale totals, recorded income, item counts/profit, debt state, and Balance final. |
| Cancel normal sale | Yes | Removes the sale from monthly reporting and can affect several KPIs. |
| Edit debt payment | Yes | Can change recorded income and Balance final, including the reporting date/month. |
| Annul debt payment | Yes | Removes reportable income and changes Balance final. |
| Edit pre-sale payment | Yes | Can change the represented sale/payment amount, quantity, date, and related KPIs. |
| Permanently delete pre-sale payment | Yes | Removes the movement from monthly reporting. |

The month/search controls, row/detail selection, disk-detail lookup, and Excel export were also inspected. They do not mutate financial data. Filter Apply/Clear intentionally load both datasets; export remains read-only.

## 4. Implementation

`LibroVentas.jsx` now has one post-mutation synchronization function:

```js
const refrescarDatosAplicados = useCallback(async () => {
  const periodoAplicado = applied.desde.slice(0, 7)
  const [movimientos] = await Promise.all([
    cargar(applied),
    cargarResumen(periodoAplicado),
  ])
  return movimientos
}, [applied, cargar, cargarResumen])
```

All six successful financial mutations call this function. It performs one movement request and one monthly-summary request in parallel. Sale and debt-payment editors continue using the refreshed movement result to keep or update the open detail panel.

The authoritative post-mutation month is derived from `applied.desde`, not editable `periodo`. This preserves the existing UX in which changing the month input has no effect until the user applies it.

The existing shared `FINANCIAL_DATA_CHANGED_EVENT` is still emitted after local refresh so other mounted financial consumers can react. `LibroVentas` does not subscribe to that event, so it does not cause a second local table or summary request.

## 5. Files modified

- `frontend/src/pages/LibroVentas.jsx` — added the centralized applied-data refresh, routed every successful financial mutation through it, and added stale-response guards for table and summary requests.
- `frontend/src/pages/LibroVentas.test.jsx` — added cancellation, annulment, applied-period, failure, and stale-response regression coverage; strengthened existing edit/delete tests to verify both datasets refresh; retained the backend-provided Balance Final assertion.
- `SALES_BOOK_REFRESH_CHANGE_REPORT.md` — documents the audit, implementation, and verification.

The backend Balance Final implementation and its backend tests were completed before this task and were not modified during this task.

## 6. Request behavior after the fix

After each successful financial mutation, the Sales Book makes exactly:

1. the action's mutation request;
2. one `GET` for the movement table using `applied`;
3. one `GET` for the monthly summary using the month derived from `applied.desde`.

This applies to:

- normal sale edit;
- normal sale cancellation;
- debt-payment edit;
- debt-payment annulment;
- pre-sale payment edit;
- pre-sale payment deletion.

The movement and summary requests run together through `Promise.all`. Previous per-handler table/summary calls and the sale editor's local table replacement were removed, so no duplicate local refresh request was introduced.

If a mutation request fails, execution never reaches `refrescarDatosAplicados`. Existing cards remain unchanged, no optimistic financial calculation is performed, and the existing error UI is preserved.

## 7. Period consistency

`periodo` remains the editable month input. `applied` remains the actual table range last committed by Apply or Clear.

Post-mutation refresh no longer reads editable `periodo`. It sends the same `applied` object to the table request and derives the summary month from `applied.desde`. Therefore, an unapplied month edit cannot split the table and cards across different periods.

Regression coverage verifies that after September 2026 is applied and the editable input is changed to August without applying it, a successful cancellation still refreshes:

- table range `2026-09-01` through `2026-09-30`;
- summary period `2026-09`.

## 8. Async/race-condition analysis

A direct stale-response risk existed because initial loads, filter applications, and mutation refreshes can overlap. Previously, whichever request resolved last could update state even if it belonged to an older range/month.

The smallest local protection was added with two monotonic request IDs stored in `useRef`:

- `ventasRequestId` protects movement data, movement loading state, and movement errors;
- `resumenRequestId` protects summary data and summary errors.

Each loader increments its ID before requesting data and updates state only if its ID is still current. Table and summary sequencing remain independent because they are independent endpoints. A regression test resolves older table and summary requests after newer requests and verifies that the newer period remains displayed.

## 9. Tests

Added or expanded coverage verifies:

- successful sale cancellation refreshes both endpoints and renders the new backend KPI values;
- successful debt-payment annulment refreshes both endpoints and renders the new backend KPI values;
- post-mutation requests use the applied September 2026 period despite an unapplied August input;
- failed sale cancellation performs no table/summary refresh and leaves the existing KPI values intact;
- normal-sale edit, debt-payment edit, pre-sale payment edit, and pre-sale payment deletion each refresh both datasets;
- stale responses from an older period cannot overwrite newer table or summary state;
- Balance final remains the value supplied by the backend and is not recalculated in React.

Executed checks:

- `npm test -- --run src/pages/LibroVentas.test.jsx` — PASS: 22 tests.
- `npm exec eslint -- src/pages/LibroVentas.jsx src/pages/LibroVentas.test.jsx` — PASS.
- `npm run build` — PASS: Vite production build completed successfully.

No backend production code was changed for this synchronization task, so no backend test run was necessary. No deployment was performed.

## 10. Financial regression confirmation

No financial formulas or accounting rules were changed. This task only coordinates frontend reads after successful mutations.

The approved backend formula remains intact in `ResumenFinancieroMensualService.obtener`:

```text
Balance final = Ingresos registrados - Gastos totales
```

Its implementation remains:

```java
.balanceFinal(ingresosRegistrados.subtract(totalGastos))
```

No formulas for Ingresos registrados, Total ventas, Ventas, Ítems vendidos, Ganancia bruta de ítems, Gastos, discounts, historical costs, EUR/UYU conversion, debt accounting, pre-sale accounting, Discogs, VinylFuture, or inventory valuation were modified.

## 11. Remaining observations

The page still intentionally separates editable and applied filters. The fix preserves that behavior rather than auto-applying input changes.

The shared financial-change event causes other screens/components that subscribe to it to refresh their own data; this pre-existing cross-screen behavior was preserved. The Sales Book itself does not subscribe, so its centralized local refresh remains exactly one movement request plus one summary request per successful mutation.

The repository contained unrelated modified and untracked files before this task, including the already completed Balance Final work. They were not reverted or otherwise changed by this synchronization implementation.

STATUS: PASS
