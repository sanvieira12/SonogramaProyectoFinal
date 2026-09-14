# Sonograma — Financial System Audit

Audit date: 2026-09-14  
Repository: `/Users/admin/Developer/sonograma`  
Commit audited: `f50e6ab` (`agent/fix-catalog-permanent-deletion`)  
Business timezone expected by the brief: `America/Montevideo` (UTC-03)

## 1. Executive Summary

This was a read-only code, schema-history, test, and deployment-configuration audit. No application code, schema, production data, frontend, backend, or configuration was modified. No production database was queried.

The principal diagnosis is that Sonograma currently has two competing representations of cash received:

1. `venta.monto_pagado` is maintained as a cumulative amount: the initial payment plus valid later debt payments.
2. `pago_deuda` separately stores each later payment as a ledger movement.

The Libro de Ventas and Dashboard then add the sale movement and the later payment movements. Therefore, after a debt payment, the same cash can be counted twice. This is **CONFIRMED** by the current code path, independent of timezone or frontend state.

Example: sale total 1,000, initially paid 400, later debt payment 600.x

| Component | Current value / movement |
|---|---:|
| `venta.monto_pagado` after payment | 1,000 |
| Sale row produced by Libro | 1,000 |
| `pago_deuda` row produced by payment | 600 |
| Current Libro total for these rows | 1,600 |
| Cash received represented once | 1,000 |

The second major finding is an **explicit timezone design gap**. Financial dates are created with `LocalDate.now()` / `LocalDateTime.now()` and persisted as unzoned `DATE` / `TIMESTAMP WITHOUT TIME ZONE`. Production Docker configuration does not set a JVM, container, or PostgreSQL timezone. As a result, a payment or sale created between approximately 21:00 and 23:59 in Uruguay can be assigned the following UTC calendar date if the production JVM defaults to UTC. That mechanism is **HIGHLY PROBABLE**, but the actual production runtime timezone was not inspected during this read-only audit.

Additional confirmed or high-risk inconsistencies:

- The Libro and Dashboard include non-anulado payments by scanning `pago_deuda` directly, while the monthly summary uses `findValidosEntre`, which also excludes inactive debts and payments tied to cancelled sales. The surfaces can therefore disagree even when their date ranges are equivalent.
- The manual-debt form uses `new Date().toISOString().slice(0, 10)`, which is a UTC date, not a Montevideo business date. This is **CONFIRMED** as a frontend date-default defect during the late-evening Uruguay window.
- Payment registration is transactionally protected and idempotent when the normal endpoint succeeds. There is no separate transaction table or write-side “Libro entry”; the Libro is a read-time aggregation of sales and debt-payment rows.
- A successful debt payment should produce a `pago_deuda` row and be visible in the Libro, but it can appear on a different day because of the date assignment, be excluded by another report because of cancellation/inactivity rules, or be hidden temporarily on another already-mounted frontend surface because the payment callback does not dispatch the global financial-change event.
- The test suites pass (`133` frontend tests; `371` backend tests, `0` failures, `0` errors, `1` skipped in the last backend run), but existing tests explicitly encode separate sale-plus-payment totals and do not assert that a cumulative `venta.monto_pagado` must be reduced to an initial-only amount for movement reporting.

Recommended order of remediation, without implementing it in this audit:

1. Define one canonical movement invariant: initial cash on the sale plus subsequent valid debt payments, or a full cash-movement ledger, but never both cumulative sale paid and payment rows for the same period.
2. Make the business clock and date conversion explicit with `America/Montevideo`; eliminate default-zone calls from financial writes and introduce deterministic clock-based tests.
3. Align cancellation, inactive-debt, and anulado-payment inclusion rules across Libro, Dashboard, monthly summary, exports, and statistics.
4. Reconcile existing production data with read-only queries before any repair is considered.
5. Add end-to-end regression coverage using PostgreSQL-compatible types and boundary times.

## 2. Architecture Map

### 2.1 Repository and runtime structure

| Layer | Evidence | Observed role |
|---|---|---|
| Frontend | `frontend/src/pages/Deudas.jsx`, `LibroVentas.jsx`, `Dashboard.jsx` | Forms, filters, local state, refetch/event behavior |
| API client | `frontend/src/api/sonograma.js` | HTTP route construction and error propagation |
| HTTP controllers | `sonograma-backend/src/main/java/com/sonograma/controller/DeudaController.java`, `VentaController.java`, `EstadisticasController.java` | REST endpoints and request parsing |
| DTOs | `sonograma-backend/src/main/java/com/sonograma/dto/*` | JSON request/response shapes |
| Services | `DeudaService`, `VentaService`, `EstadisticasService`, `ResumenFinancieroMensualService` | Business rules, aggregation, transaction boundaries |
| Repositories | `sonograma-backend/src/main/java/com/sonograma/repository/*` | JPA/native reads and pessimistic locks |
| Entities | `sonograma-backend/src/main/java/com/sonograma/entity/Deuda.java`, `PagoDeuda.java`, `Venta.java`, `DetalleVenta.java` | Persistent financial state |
| Database | PostgreSQL in production; H2 in most tests | `venta`, `deuda`, `pago_deuda`, details, stock and expense tables |
| Deployment | `docker-compose.prod.yml`, `sonograma-backend/Dockerfile`, `deploy/deploy.sh` | Production process, database, migration application |

Production Spring profile uses `/api` as the context path and `spring.jpa.hibernate.ddl-auto=validate` in `sonograma-backend/src/main/resources/application-prod.properties:7-32`. The normal application profile uses PostgreSQL configuration and `ddl-auto=update` in `application.properties`.

### 2.2 Financial routes

| User operation | Frontend | HTTP endpoint | Main backend path |
|---|---|---|---|
| Create/edit manual debt | `Deudas.jsx` | `POST/PUT /api/deudas` | `DeudaController` → `DeudaService.crear/actualizar` |
| Register debt payment | `Deudas.jsx` | `POST /api/deudas/{id}/registrar-pago` or `/pagos` | `DeudaController.registrarPago` → `DeudaService.registrarPago` |
| Delete debt payment | Libro/Deudas UI | `DELETE /api/deudas/pagos/{idPagoDeuda}` or nested alias | `DeudaController.eliminarPago` → `DeudaService.eliminarPago` |
| Create sale | `NuevaVenta.jsx` and related flows | `POST /api/ventas` | `VentaController.registrarVenta` → `VentaService.registrarVenta` |
| Cancel sale | Libro UI | `DELETE /api/ventas/{id}` | `VentaController.cancelarVenta` → `VentaService.cancelarVenta` |
| Read Libro | `LibroVentas.jsx`, Dashboard | `GET /api/ventas/libro` | `VentaService.obtenerLibro` |
| Read monthly summary | `LibroVentas.jsx` | `GET /api/ventas/resumen-mensual` | `ResumenFinancieroMensualService.obtener` |
| Read Dashboard income | `Dashboard.jsx` | `GET /api/estadisticas/ingresos?periodo=...` | `EstadisticasService.obtenerSerieIngresos` |
| Export Libro | `LibroVentas.jsx` | `GET /api/ventas/libro/exportar` | `ExcelExportService` over `VentaService.obtenerLibro` |

### 2.3 Canonical data relationships

```text
Cliente
  └── Venta (many sales)
        ├── DetalleVenta (sale items)
        └── Deuda (optional, one-to-one by deuda.id_venta)
              └── PagoDeuda (many payment movements)

Libro / Dashboard / monthly summary
  ├── sale-derived movement from Venta
  └── payment-derived movement from PagoDeuda
```

There is no separate `transaccion`, `movimiento_financiero`, or immutable cash-journal entity. The “Libro entry” is reconstructed at read time from `Venta` and `PagoDeuda`.

## 3. Debt Lifecycle

### 3.1 Creation

`DeudaController.crear` accepts `DeudaRequestDTO` and delegates to `DeudaService.crear`. `DeudaService` is class-level `@Transactional` (`DeudaService.java:41-44`). Creation applies the request, resolves or creates a client for manual debt data, initializes the paid amount, calculates pending amount/state, and saves the debt.

For a manually created debt, the initial paid value is stored in `monto_pagado_inicial`; there may be no sale. For a sale-linked debt, `VentaService` calls `DeudaService.sincronizarVenta` after the sale has been built and saved.

### 3.2 Sale-linked debt creation

`VentaService.registrarVenta` calculates the sale totals and derives:

- `Venta.montoPagado` from the request, defaulting to the total when no amount is supplied;
- `Venta.montoDeuda` as total minus paid;
- `Venta.estadoPago` as paid, partial, or pending.

It then calls `deudaService.sincronizarVenta`. `sincronizarVenta` locks the client, finds or creates the one-to-one debt for the sale, stores the initial amount in `monto_pagado_inicial`, maps the sale date to `Deuda.fecha_venta` and `fecha_deuda`, recalculates the balance, and saves it.

### 3.3 Balance formula

`DeudaService.calcularBalance` (`DeudaService.java:582-591`) computes:

```text
paid = min(max(monto_pagado_inicial, 0) + sum(valid positive PagoDeuda.monto, monto_total)
pending = max(monto_total - paid, 0)
estado = state(total, paid)
```

`recalcularEstado` (`DeudaService.java:594-604`) writes the cached debt values and, when a sale is associated, also writes:

- `venta.monto_pagado = paid`;
- `venta.monto_deuda = pending`;
- `venta.estado_pago = debt state`.

This is the central source of the later Libro/Dashboard double count: the sale’s paid field is cumulative, while the payment rows remain separately available as movements.

### 3.4 Editing

`DeudaService.actualizar` recalculates from payment rows and rejects arbitrary modification of the paid amount through `validarMontoPagadoNoEditable`. That protects the active debt from simply changing its paid total without payment history, but it does not solve the reporting semantics of a cumulative sale paid field.

`VentaService.actualizarVenta` can rebuild sale details, totals, stock, shipping, and the linked debt in one transaction. The audit did not find an explicit business rule that prevents editing a sale after it has accumulated payment rows. That is a **BUSINESS RULE AMBIGUITY / HIGH-RISK AREA**: changing total or initial payment after subsequent debt payments may alter the inferred initial balance and historical cash meaning.

### 3.5 Cancellation and deletion

`VentaService.cancelarVenta` restores stock, marks the sale `CANCELADA`, and marks the linked debt inactive. It does not reverse or delete the `PagoDeuda` rows. The current Libro and Dashboard payment scans can still include those rows because they check `anulado`, not debt activity or sale status.

`DeudaService.eliminarPago` currently physically deletes the payment row (`pagoDeudaRepository.delete`), flushes, recomputes the debt, and saves it. This is semantically different from migration 031, whose comments describe retained/anulado payment rows that are excluded from balances and reporting. That mismatch is **CONFIRMED historical semantic drift**.

## 4. Debt Payment Flow

### 4.1 Request and response path

`Deudas.jsx:120-146` validates a positive amount, creates or reuses an idempotency key, and sends the amount, notes, receipt, and key. `DeudaController.registrarPago` accepts a generic `Map<String,Object>`, supports both `numeroRecibo` and the legacy alias `numeroBoleta`, and calls the service.

`DeudaService.registrarPago`:

1. Locks the active debt with `findByIdForUpdate`.
2. Checks the optional `(id_deuda, idempotency_key)` lookup.
3. Recalculates current balance from initial amount plus valid payments.
4. Rejects already-paid, non-positive, or excessive payments.
5. Creates and saves `PagoDeuda`.
6. Assigns `fechaPago=LocalDate.now()` and updates debt `fechaUltimoPago=LocalDate.now()`.
7. Recalculates the debt and associated sale cached totals.
8. Saves the debt and returns `DeudaResponseDTO`.

The normal success path therefore writes a payment movement. It does not write a Libro row; the Libro discovers the payment later.

### 4.2 What is persisted

`PagoDeuda` (`sonograma-backend/src/main/java/com/sonograma/entity/PagoDeuda.java`) contains:

- `id_deuda` foreign key, non-null;
- `monto`, non-null;
- `fecha_pago` as `LocalDate` / database `DATE`;
- notes and receipt;
- nullable idempotency key;
- `created_at` as `LocalDateTime` / database timestamp;
- `anulado`, `fecha_anulacion`, and `anulado_por`.

The payment has both a business date and a creation timestamp, but Libro prefers the business date through `IngresoLibroCalculator.fechaPago` and converts it to midnight. The exact time of the payment is therefore not represented in the Libro movement.

### 4.3 Concurrency and idempotency

The pessimistic debt lock prevents two normal payment requests from calculating against the same pending balance simultaneously. Migration 030 adds a partial unique index for non-null idempotency keys per debt. This is **NOT A BUG** in the normal endpoint design.

The idempotent retry returns the current debt DTO when the same key is found; it does not create a second payment. Receipt numbers are not shown as having a unique constraint, so duplicate receipt numbers remain a possible data-quality issue unless intentionally allowed.

### 4.4 When a successful payment can appear missing

The code does not show a normal successful payment being silently omitted from `pago_deuda`. The following are the realistic ways it can look missing from a user’s expected view:

- `fecha_pago` is assigned the next calendar day under a default UTC JVM, so a “today” filter excludes it.
- The Libro request has a date range or channel filter; payment rows are only included when no channel filter is supplied.
- The Dashboard or another tab has stale state because the payment callback reloads the debt page but does not dispatch `FINANCIAL_DATA_CHANGED_EVENT`.
- A separate report excludes inactive/cancelled related debts while the Libro includes them.
- A legacy/import/direct-database path changed cached debt values without creating a corresponding payment row.
- The payment was deleted by the current delete endpoint, or an older/history path physically removed it.

## 5. Libro de Ventas

### 5.1 Normal sale rows

`VentaService.obtenerLibro` (`VentaService.java:400-421`) first obtains all non-cancelled sales, applies optional date/channel/search filters in memory, and maps each sale to `VentaResponseDTO`.

The sale movement amount comes from `IngresoLibroCalculator.montoVenta`:

```java
return venta.getMontoPagado() != null
        ? venta.getMontoPagado()
        : VentaTotals.totalProductos(venta);
```

Thus, for ordinary records, Libro displays the current cumulative `venta.monto_pagado`, not necessarily the amount paid at the time the sale row was created.

`VentaTotals.totalProductos` calculates from details, subtotal, price, or stored totals depending on available fields; it is not identical to every persisted total field. This is intentional fallback logic for legacy/multi-item records, but it creates another reconciliation dimension when historical rows are incomplete.

### 5.2 Debt-payment rows

When `canal` is blank, Libro scans `pagoDeudaRepository.findAll()` and filters only `anulado=false` (`VentaService.java:410-416`). It maps each payment to a synthetic `VentaResponseDTO` with:

- `tipoMovimiento="PAGO_DEUDA"`;
- `idPagoDeuda` and `idDeuda`;
- related sale ID, if any;
- payment amount as total, total final, paid amount, and movement amount;
- zero debt;
- date from `IngresoLibroCalculator.fechaPago`.

The payment row is therefore visible for a manual debt as well as a sale-linked debt.

### 5.3 Filters and dates

`parseDesde` maps a date to start of day and `parseHasta` maps a date to `LocalTime.MAX`. Both sale and payment filtering are inclusive in the in-memory path. Payments are not filtered by `activa`, related sale status, or debt cancellation.

The repository also has a `buscarLibro` query, but the current `obtenerLibro` path does not use it. This means the actual production behavior is the service’s in-memory `findAllByOrderByFechaVentaDesc` plus `findAll` payment scan, not the unused repository query.

### 5.4 Confirmed duplicate movement scenario

The following sequence is sufficient to reproduce the overcount without any timezone issue:

1. Create a sale for 1,000 with 400 paid and 600 debt.
2. `sincronizarVenta` stores initial paid 400.
3. Register a 600 payment.
4. `recalcularEstado` stores `venta.monto_pagado=1,000`.
5. Libro maps the sale as 1,000 and the payment as 600.

Finding: **CONFIRMED BUG — cumulative sale payment plus separate debt-payment movement are both reported as income movements.**

## 6. Home / Dashboard

### 6.1 Data sources

`Dashboard.jsx` has separate effects:

- `api.libro.listar({})` populates the recent movements table.
- `api.estadisticas.ingresos(periodoIngresos)` populates the income series/KPI.
- `api.gastosTienda.resumen()` populates current-month expenses.
- inventory is loaded separately.

The recent table and the graph do not use the same HTTP response, although the backend comment in `IngresoLibroCalculator` says Dashboard should aggregate the same movements as Libro.

### 6.2 Income-series aggregation

`EstadisticasService.ingresosVigentes` includes non-cancelled sales and all non-anulado payments. `agruparIngresos` adds:

- `IngresoLibroCalculator.montoVenta(venta)` under the sale date;
- each payment amount under `IngresoLibroCalculator.fechaPago(pago)`.

Because `montoVenta` reads the cumulative sale paid field after `recalcularEstado`, Dashboard repeats the same double-counting defect as Libro.

Finding: **CONFIRMED BUG — Dashboard income totals and series can overstate revenue after debt payments.**

### 6.3 Dashboard versus monthly summary

The monthly summary uses:

- non-cancelled sales in a period;
- `findValidosEntre` for payment rows, which additionally requires active debt and non-cancelled related sale through its query;
- `venta.monto_pagado` as sale-date income;
- payment rows as separate income.

So it shares the double-counting issue for active, non-cancelled debts and additionally diverges on cancellation/inactive filtering.

### 6.4 Daily chart edge

`EstadisticasService.SeriePeriodo.DIA` constructs each hourly bucket with `fin=dia.atTime(hora,59,59)` rather than the last nanosecond of the hour. A movement at `HH:59:59.x` can be included in the full-day total but excluded from its hourly bucket. This is a **CONFIRMED LOW-SEVERITY aggregation defect**; it does not by itself explain a next-day record.

## 7. Comparison: Debt, Libro, Home, and Monthly Summary

| Dimension | Debt screen | Libro | Dashboard income | Monthly summary |
|---|---|---|---|---|
| Sale inclusion | Active/pending debt-oriented views; cancelled sale excluded | Sale not cancelled | Sale not cancelled | Sale not cancelled |
| Payment inclusion | Valid non-anulado rows for balance | `findAll`, only non-anulado | `findAll`, only non-anulado | `findValidosEntre`, also active/non-cancelled relationship |
| Sale paid value | Cumulative balance | Uses cumulative `venta.monto_pagado` | Uses same calculator | Uses cumulative `venta.monto_pagado` |
| Later payment row | Balance component | Separate synthetic movement | Separate income movement | Separate income component |
| Date source for payment | `fecha_pago` / `fecha_ultimo_pago` as `LocalDate` | `fecha_pago` at midnight | Same payment date at midnight | Repository `fecha_pago` range |
| Cancellation semantics | Debt inactive; payment history retained | Can still show payment | Can still count payment | Excludes related inactive/cancelled payment |
| Channel filter | Not applicable | Payment rows omitted when channel is supplied | Not applicable | Not applicable |
| Refresh after payment | Debt page reloads locally | Event only if dispatched elsewhere | Depends on global event | Refetch on page effect/event |

Confirmed conclusions:

- Home and Libro can disagree because their backend aggregations share the cumulative-sale-plus-payment problem but do not necessarily receive the same date/range/filter or state.
- Libro and monthly summary can disagree because payment validity predicates differ.
- Cancellation inclusion is not classifiable as universally wrong without a business decision about whether a cancellation means refund/reversal or merely removing the sale from inventory/sales activity. The disagreement itself is **CONFIRMED**.

## 8. Date and Timezone Audit

### 8.1 Backend types

The financial date fields are split as follows:

| Field | Java type | Expected DB type | Meaning risk |
|---|---|---|---|
| `Venta.fechaVenta` | `LocalDateTime` | `TIMESTAMP WITHOUT TIME ZONE` | Server-local wall time with no zone metadata |
| `Deuda.fechaDeuda`, `fechaVenta`, `fechaUltimoPago` | `LocalDate` | `DATE` | Calendar date depends on writer’s clock |
| `PagoDeuda.fechaPago` | `LocalDate` | `DATE` | Payment calendar date only; no exact time |
| `PagoDeuda.createdAt` | `LocalDateTime` | timestamp without zone | Creation wall time with no zone metadata |
| `PreVenta.fecha` | `LocalDate` | date | Business date |
| `PreVenta.fechaPago` | `LocalDateTime` | timestamp without zone | Same server/browser zone risk |

### 8.2 Default-zone calls

Relevant financial writes use default-zone calls:

- `VentaService.registrarVenta`: `dto.fechaVenta` or `LocalDateTime.now()`.
- `DeudaService.sincronizarVenta`: `LocalDateTime.now()` for update timestamp.
- `DeudaService.registrarPago`: `LocalDate.now()` for payment date and last-payment date; `LocalDateTime.now()` for update timestamp.
- Entity defaults in `Venta`, `Deuda`, and `PagoDeuda` also initialize timestamps with `now()`.

No explicit business `ZoneId` is used at those write points.

### 8.3 Explicit Uruguay usage is incomplete

`EstadisticasService` and `ResumenFinancieroMensualService` use `ZoneId.of("America/Montevideo")` when choosing the current day/month boundary. That makes report-period selection explicit, but it does not change how persisted sale/payment dates were originally assigned.

This creates a split model:

```text
write date: JVM default zone
current report boundary: America/Montevideo
stored timestamp: no timezone metadata
frontend manual default: UTC via toISOString()
```

### 8.4 Production configuration

`sonograma-backend/Dockerfile:10-16` runs `eclipse-temurin:21-jre-alpine` with only `-Dserver.port`; it does not set `-Duser.timezone`. `docker-compose.prod.yml:40-55` does not set `TZ` for backend or PostgreSQL. `application-prod.properties` has no timezone setting.

This is **HIGHLY PROBABLE production risk**, not a runtime proof. The exact production JVM/container timezone must be checked operationally before assigning individual incidents.

### 8.5 Frontend date behavior

`Deudas.jsx:20` and `Deudas.jsx:50` use `new Date().toISOString().slice(0, 10)` for default manual debt dates. `toISOString()` is UTC. In Montevideo, from 21:00 until local midnight, that string represents the next calendar date. This is **CONFIRMED frontend logic** and **HIGHLY PROBABLE user-visible date error** in that time window.

`Deudas.jsx:30-33` formats a stored date by appending `T00:00:00`, which avoids the common UTC parsing shift for date-only display when the browser is in Uruguay.

`LibroVentas.jsx` builds date input values from local browser fields. `Dashboard.jsx` uses `new Date(fechaStr).toLocaleDateString('es-UY')` for unzoned backend datetimes; a browser outside Uruguay can display a different local calendar date from the server’s intended business date.

`NuevaVenta.jsx` commonly sends no `fechaVenta`, leaving the backend to use the server default timezone.

### 8.6 Answers on “next-day” behavior

Can a debt appear one day late? **Yes, HIGHLY PROBABLE under an unconfigured UTC production JVM**, especially between 21:00 and 23:59 Uruguay. It is also directly possible for manually created debt form defaults because of `toISOString()`.

Can a successful payment be in the next day’s Libro? **Yes, if `LocalDate.now()` resolves to the next UTC date.** It will still exist as a payment row, but a date filter for the Uruguay calendar day can miss it.

Was the actual production timezone proven? **No.** Use the read-only runtime/DB checks in section 15 before claiming a specific production incident.

## 9. Database Consistency

### 9.1 Schema history

The repository has manually applied SQL under `docs/migraciones`. There is no Flyway dependency in `sonograma-backend/pom.xml` and no active `src/main/resources/db/migration` Flyway directory. `db/migration_descontinuado.sql` is not an active migration chain.

`deploy/deploy.sh:42-52` loops over all migration files and continues after a migration command fails, logging a warning. In production profile, Hibernate is set to validate. This combination makes schema state dependent on migration history and on whether a failed/idempotent migration was safe to skip. It is a **CONFIRMED deployment/process risk**, not a proven current database defect.

### 9.2 Relevant migrations

- `docs/migraciones/012_management_refactor_clientes_deudas_pedidos_notas.sql` creates/extends debt and payment structures, including date-only payment fields and timestamp defaults.
- `docs/migraciones/013_deuda_activa.sql` adds the active flag.
- `docs/migraciones/029_deudas_balance_por_movimiento.sql` normalizes initial balance from cached debt/sale paid values minus payment rows.
- `docs/migraciones/030_pago_deuda_recibo_idempotencia.sql` adds receipt/idempotency columns and the partial idempotency uniqueness rule.
- `docs/migraciones/031_pago_deuda_anulacion.sql` adds anulado/reversal metadata and documents retained reversed rows being excluded from balances/reports/exports.

Migration 029’s normalization sums payment rows without an anulado predicate. At the time of its original introduction anulado may not yet have existed, but replaying normalization after reversals can be dangerous. This is a **HISTORICAL REPLAY RISK**.

### 9.3 Constraints and missing invariants

Observed useful constraints:

- `deuda.id_venta` is one-to-one/unique when present.
- `pago_deuda.id_deuda` is non-null foreign-keyed.
- idempotency key uniqueness is partial and scoped per debt.
- numeric fields are non-null in the entity/schema where current records are expected.

Not observed as database-enforced invariants:

- payment amount must be positive;
- cumulative paid must equal initial paid plus valid payment sum;
- pending must equal total minus paid;
- sale cached paid/debt must agree with its debt;
- receipt number uniqueness;
- one canonical rule for active/cancelled payment inclusion;
- immutable historical initial payment after later settlements.

The service enforces several of these on the normal write path, but read-time reconciliation must assume legacy/import/manual data can violate them.

### 9.4 Legacy data

Early migrations backfill details and make `venta.id_disco` nullable, but the audit did not find a single complete backfill for all later sale payment fields. Seed and historical rows can therefore have null newer fields. Current fallback code handles some nulls, but it makes old rows participate differently in Libro/statistics calculations.

## 10. Transaction Boundaries

### 10.1 Normal sale creation

`VentaService` is class-level `@Transactional`. Sale creation, detail persistence, stock reservation, profit updates, debt synchronization, and shipping synchronization join the same Spring transaction. If an exception propagates, the normal path is atomic.

### 10.2 Normal debt payment

`DeudaService` is class-level `@Transactional`. Payment insert, debt recalculation, associated sale dirty-state updates, and debt save occur in one transaction. The pessimistic debt lock and unique idempotency index protect the normal concurrent request path.

Finding: **NOT A BUG for the normal payment transaction boundary.** The remaining issue is what the transaction writes and how read surfaces interpret it.

### 10.3 Payment deletion/reversal

Payment deletion and recalculation are also within the service transaction. However, physical deletion conflicts with the retained/anulado design described by migration 031. This is a semantic/auditability defect, not an atomicity defect.

### 10.4 Excel debt import

`DeudaController.importarExcel` is annotated `@Transactional` but catches exceptions inside the per-row loop and continues, returning counts/errors. Depending on exception type and Spring transaction state, a caught persistence exception can mark the enclosing transaction rollback-only or hide partial behavior. This path needs a focused integration test; it is **POSSIBLE/HIGH-RISK**, not a confirmed debt-payment production defect.

### 10.5 No transactional ledger write

There is no transaction that creates a separate financial movement record. A committed payment is authoritative only as a `PagoDeuda` row plus updated cached fields. Reporting correctness depends on all read services applying the same movement policy.

## 11. Frontend State and Refresh

### 11.1 Payment form state

`Deudas.jsx` clears the payment form after a successful response and calls `onPaid(updated)`. The parent `upsert` reloads the debt collection and focuses the updated debt. Error responses are surfaced to the user; the API client rejects non-2xx responses. There is no evidence that the normal payment success is swallowed by the frontend.

### 11.2 Global financial-change event

Dashboard listens for `FINANCIAL_DATA_CHANGED_EVENT` and refetches Libro recent movements, expenses, and income series. Libro dispatches the event after its own sale/debt actions. Deudas dispatches it after debt deletion (`Deudas.jsx:344-350`).

The debt payment path calls `onPaid(updated)` but does not dispatch the event. Therefore, if Dashboard or Libro is already mounted in the same browser context, those surfaces can remain stale after payment until their own refetch/remount. This is **HIGHLY PROBABLE frontend refresh gap**. It is not a backend persistence failure and does not explain a date shift by itself.

Cross-tab synchronization is not provided by a same-window event either. A second browser tab will not reliably update from a payment performed in the first tab without navigation/refetch.

### 11.3 Different endpoint responses

Dashboard’s recent movement table uses `/ventas/libro` with no range, while its graph uses `/estadisticas/ingresos` with a period. Libro page uses `/ventas/libro` and `/ventas/resumen-mensual`. Even with correct backend rules, these endpoints require explicit date and inclusion contracts to remain comparable.

### 11.4 React key risk

Dashboard recent rows use the underlying `idVenta` as the row key. A sale-linked debt payment DTO also carries the underlying sale ID, so sale and payment rows can share a key. This is a **POSSIBLE low-severity UI reconciliation issue**; Libro’s table uses a composite type/payment-or-sale key and is safer.

## 12. Historical Risks and Evolution

Git history shows several successive changes around debt payments, Libro, dashboard income, reversals, receipts, and balance consolidation (`54d9a5a`, `3dba984`, `eb1ec0f`, `65a10a7`, `51ee97a`, `11927cc`, `61f3792`). The sequence indicates the ledger behavior has been repeatedly adjusted rather than originating from one immutable financial model.

Important historical signals:

- Commit `eb1ec0f` introduced/reused `IngresoLibroCalculator` to unify Dashboard and Libro movement logic.
- Later payment/receipt changes made payment business date take precedence over creation timestamp.
- Migration 031 defines logical reversal semantics, while current service code deletes rows physically.
- Migration 029 derives initial payment from cached paid minus payment sum, making reruns sensitive to the current contents and status of payment rows.

These changes raise the chance of historical records having mixed semantics: some sale rows may mean total contract value, some current cumulative received amount, and some legacy rows may have null payment fields. The application’s fallback logic prevents many crashes but cannot infer the original cash event reliably in every case.

## 13. Confirmed Bugs

### C-01 — Sale-plus-payment double count

Severity: Critical financial correctness.  
Evidence: `DeudaService.recalcularEstado:594-604`; `IngresoLibroCalculator.montoVenta:17-21`; `VentaService.obtenerLibro:406-416`; `EstadisticasService:109-127,131-155`; `ResumenFinancieroMensualService:61-65,113-140`.

`venta.monto_pagado` becomes cumulative after a later payment, while the same payment remains a separate `pago_deuda` movement. All three reporting paths add both.

Impact: inflated Libro totals, Dashboard totals/series, and monthly registered income. The exact amount overcounted for a sale-linked debt is generally the sum of valid later payments once the sale’s paid field already includes them.

### C-02 — Inconsistent payment validity predicates

Severity: High reconciliation risk.  
Evidence: `VentaService.obtenerLibro` and `EstadisticasService.pagosVigentes` filter only `anulado=false`; `PagoDeudaRepository.findValidosEntre` additionally filters active debt and non-cancelled sale.

Impact: Libro/Dashboard can count a payment that monthly summary excludes, particularly after sale cancellation or debt deactivation.

Whether the payment should remain as cash after cancellation is a business decision. The disagreement between surfaces is confirmed regardless of that decision.

### C-03 — Manual debt UTC default date

Severity: Medium, time-window dependent.  
Evidence: `frontend/src/pages/Deudas.jsx:20,50`.

`toISOString().slice(0,10)` chooses UTC date. During 21:00–23:59 Montevideo, a new manual debt can default to tomorrow.

### C-04 — Hourly bucket excludes sub-second final second

Severity: Low.  
Evidence: `EstadisticasService.java:353-360`.

The hour end is `HH:59:59`, not `HH:59:59.999999999`, so events in the fractional final second can be absent from the bucket while included in the day total.

### C-05 — Reversal implementation contradicts migration semantics

Severity: High auditability risk.  
Evidence: `docs/migraciones/031_pago_deuda_anulacion.sql`; `DeudaService.eliminarPago:358-388`.

The migration documents retained/anulado rows, while current code physically deletes the payment. This can destroy the audit trail and makes historical reports dependent on which code version handled the reversal.

## 14. Probable Bugs

### P-01 — Production UTC/default-zone date shifts

Severity: High.  
Evidence: default-zone writes in `VentaService`, `DeudaService`, entities; no timezone in `Dockerfile`, `docker-compose.prod.yml`, or prod properties.

If the production JVM defaults to UTC, late-evening Uruguay operations receive the next UTC calendar date. Confirm runtime settings before data repair.

### P-02 — Dashboard/Libro stale after debt payment

Severity: Medium UX/data-visibility risk.  
Evidence: `Deudas.jsx:131-140` calls `onPaid` without dispatch; Dashboard and Libro depend on `FINANCIAL_DATA_CHANGED_EVENT`.

The debt screen updates, but another mounted surface may retain old values until a refetch/remount.

### P-03 — Production schema drift or partial migration state

Severity: High operational risk.  
Evidence: no Flyway dependency; manually replayed migration loop; deployment continues after migration failures; production relies on Hibernate validate.

This does not prove current drift, but it makes drift and partial history plausible and hard to diagnose from application code alone.

### P-04 — Editing a sale after later payments changes historical meaning

Severity: Medium/high business-rule risk.

The system permits sale updates and re-synchronizes debt. No explicit immutable boundary was found for initial paid amount, total, or payment history. A total/payment edit after later settlements may reinterpret the balance.

### P-05 — Payment filtering can disagree with cancellation intent

Severity: Medium.

Current tests explicitly expect Libro and Dashboard to retain payments related to inactive/cancelled sales. If the intended rule is “cancelled means refund/no income,” those tests and implementation are wrong. If the intended rule is “payment was real cash and remains income,” monthly summary’s stricter repository query is wrong.

## 15. Potential Data Problems — SELECT-Only Diagnostics

The following queries are diagnostics only. They were not executed by this audit. Run them against a read-only production connection, record the result, and preserve the output before considering any repair.

### 15.1 Runtime/database calendar evidence

```sql
SELECT current_setting('TimeZone') AS postgres_timezone,
       current_date AS postgres_date,
       current_timestamp AS postgres_timestamp,
       now() AT TIME ZONE 'America/Montevideo' AS montevideo_local;
```

Also inspect the running backend container/JVM with an operational read-only command: OS `date`, Java default zone, and process environment. The goal is to distinguish a real UTC JVM from a merely unconfigured file.

### 15.2 Schema types and defaults

```sql
SELECT table_name,
       column_name,
       data_type,
       udt_name,
       is_nullable,
       column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('venta', 'deuda', 'pago_deuda', 'detalle_venta', 'gasto_tienda')
ORDER BY table_name, ordinal_position;
```

### 15.3 Debt cached-balance invariant

```sql
SELECT d.id_deuda,
       d.id_venta,
       d.monto_total,
       d.monto_pagado_inicial,
       d.monto_pagado,
       d.monto_pendiente,
       d.estado_pago,
       d.activa,
       COALESCE(SUM(p.monto) FILTER (
           WHERE COALESCE(p.anulado, false) = false
             AND p.monto > 0
       ), 0) AS pagos_validos,
       COALESCE(d.monto_pagado_inicial, 0)
         + COALESCE(SUM(p.monto) FILTER (
             WHERE COALESCE(p.anulado, false) = false
               AND p.monto > 0
           ), 0) AS paid_expected_before_cap,
       v.monto_pagado AS venta_monto_pagado,
       v.monto_deuda AS venta_monto_deuda,
       v.estado AS venta_estado
FROM deuda d
LEFT JOIN pago_deuda p ON p.id_deuda = d.id_deuda
LEFT JOIN venta v ON v.id_venta = d.id_venta
GROUP BY d.id_deuda, d.id_venta, d.monto_total, d.monto_pagado_inicial,
         d.monto_pagado, d.monto_pendiente, d.estado_pago, d.activa,
         v.monto_pagado, v.monto_deuda, v.estado
HAVING d.monto_pagado IS DISTINCT FROM LEAST(
           GREATEST(COALESCE(d.monto_total, 0), 0),
           GREATEST(COALESCE(d.monto_pagado_inicial, 0), 0)
             + COALESCE(SUM(p.monto) FILTER (
                 WHERE COALESCE(p.anulado, false) = false
                   AND p.monto > 0
               ), 0)
       )
    OR d.monto_pendiente IS DISTINCT FROM GREATEST(
           COALESCE(d.monto_total, 0)
           - LEAST(
               GREATEST(COALESCE(d.monto_total, 0), 0),
               GREATEST(COALESCE(d.monto_pagado_inicial, 0), 0)
                 + COALESCE(SUM(p.monto) FILTER (
                     WHERE COALESCE(p.anulado, false) = false
                       AND p.monto > 0
                   ), 0)
             ),
           0
       )
    OR d.id_venta IS NOT NULL
       AND v.monto_pagado IS DISTINCT FROM d.monto_pagado;
```

### 15.4 Candidate duplicate Libro movements

```sql
SELECT v.id_venta,
       v.total_final,
       v.monto_pagado AS sale_paid_used_by_libro,
       COALESCE(d.monto_pagado_inicial, 0) AS initial_paid,
       COALESCE(SUM(p.monto) FILTER (
           WHERE COALESCE(p.anulado, false) = false
             AND p.monto > 0
       ), 0) AS later_payments,
       v.monto_pagado
         + COALESCE(SUM(p.monto) FILTER (
             WHERE COALESCE(p.anulado, false) = false
               AND p.monto > 0
           ), 0) AS current_reported_pair_total
FROM venta v
JOIN deuda d ON d.id_venta = v.id_venta
LEFT JOIN pago_deuda p ON p.id_deuda = d.id_deuda
WHERE v.estado <> 'CANCELADA'
GROUP BY v.id_venta, v.total_final, v.monto_pagado, d.monto_pagado_inicial
HAVING COALESCE(SUM(p.monto) FILTER (
           WHERE COALESCE(p.anulado, false) = false
             AND p.monto > 0
       ), 0) > 0
   AND v.monto_pagado > COALESCE(d.monto_pagado_inicial, 0);
```

### 15.5 Payments attached to inactive/cancelled records

```sql
SELECT p.id_pago_deuda,
       p.id_deuda,
       p.monto,
       p.fecha_pago,
       p.created_at,
       p.anulado,
       d.activa,
       d.id_venta,
       v.estado AS venta_estado
FROM pago_deuda p
JOIN deuda d ON d.id_deuda = p.id_deuda
LEFT JOIN venta v ON v.id_venta = d.id_venta
WHERE COALESCE(p.anulado, false) = false
  AND (d.activa = false OR v.estado = 'CANCELADA')
ORDER BY p.fecha_pago, p.id_pago_deuda;
```

### 15.6 Invalid or suspicious monetary values

```sql
SELECT 'deuda' AS source, id_deuda AS id, monto_total AS amount
FROM deuda
WHERE monto_total IS NULL OR monto_total < 0
UNION ALL
SELECT 'deuda_paid', id_deuda, monto_pagado
FROM deuda
WHERE monto_pagado IS NULL OR monto_pagado < 0
UNION ALL
SELECT 'deuda_pending', id_deuda, monto_pendiente
FROM deuda
WHERE monto_pendiente IS NULL OR monto_pendiente < 0
UNION ALL
SELECT 'pago_deuda', id_pago_deuda, monto
FROM pago_deuda
WHERE monto IS NULL OR monto <= 0
ORDER BY source, id;
```

### 15.7 Duplicate idempotency keys and receipts

```sql
SELECT id_deuda, idempotency_key, COUNT(*) AS repetitions
FROM pago_deuda
WHERE idempotency_key IS NOT NULL
GROUP BY id_deuda, idempotency_key
HAVING COUNT(*) > 1;

SELECT numero_recibo, COUNT(*) AS repetitions
FROM pago_deuda
WHERE numero_recibo IS NOT NULL AND BTRIM(numero_recibo) <> ''
GROUP BY numero_recibo
HAVING COUNT(*) > 1;
```

### 15.8 Date boundary review

```sql
SELECT id_venta,
       fecha_venta,
       fecha_venta::date AS stored_calendar_date,
       (fecha_venta AT TIME ZONE 'America/Montevideo') AS interpreted_montevideo_timestamp
FROM venta
WHERE fecha_venta IS NOT NULL
ORDER BY fecha_venta DESC;

SELECT id_pago_deuda,
       id_deuda,
       fecha_pago,
       created_at,
       (created_at AT TIME ZONE 'America/Montevideo') AS interpreted_montevideo_timestamp
FROM pago_deuda
WHERE created_at IS NOT NULL
ORDER BY created_at DESC;
```

For `timestamp without time zone`, `AT TIME ZONE` is an interpretation, not proof of the original zone. Compare the result with operator-entered dates and deploy/runtime logs.

### 15.9 Orphan checks

```sql
SELECT p.id_pago_deuda
FROM pago_deuda p
LEFT JOIN deuda d ON d.id_deuda = p.id_deuda
WHERE d.id_deuda IS NULL;

SELECT d.id_deuda, d.id_venta
FROM deuda d
LEFT JOIN venta v ON v.id_venta = d.id_venta
WHERE d.id_venta IS NOT NULL
  AND v.id_venta IS NULL;
```

Foreign keys should make these empty; they are still useful when checking historical imports or constraint differences.

## 16. Business Rule Ambiguities

The following decisions must be explicit before a safe fix or data repair:

1. Does a sale row represent contract value, total cash received to date, or only initial cash received? Current code uses cumulative cash received while also treating later payments as separate movements.
2. Should Libro income be cash-basis or sale-date accrual? Current code mixes sale-date sale rows with payment-date later rows.
3. When a sale is cancelled, should already received money remain income, become a reversal/refund, or be excluded until a refund movement exists?
4. Should an inactive manual debt’s valid payment remain in Libro and Dashboard?
5. Is a payment’s business date supplied by the operator, determined by Montevideo current date, or derived from exact creation timestamp?
6. Can an operator edit sale total or initial paid amount after later debt payments exist?
7. Is deleting a payment allowed, or must every correction be an immutable reversal/anulado movement?
8. Should receipt numbers be unique globally, per sale, per debt, or not at all?
9. Should channel filters include standalone debt payments? Current Libro omits all payment rows when `canal` is supplied.
10. Does `balanceFinal` mean income, income minus expenses, or another financial metric? Current monthly summary assigns it to `ingresosRegistrados` while exposing expenses separately.

## 17. Recommended Fixes — Not Implemented in This Audit

The following are recommendations only; none was implemented.

### Priority 0 — Correct the financial model

- Choose a canonical cash-movement invariant.
- Prefer an explicit initial-sale movement plus subsequent payment movements, or store only sale contract/accrual value and derive cash separately. Do not use cumulative `venta.monto_pagado` as a sale movement while also adding its component payments.
- Centralize movement construction and inclusion predicates so Libro, export, Dashboard, and monthly summary consume the same definition.
- Add a reconciliation metric that compares cached fields with derived fields and exposes mismatches without silently correcting them.

### Priority 1 — Make time deterministic

- Inject a `Clock` configured for `America/Montevideo` at financial write points.
- Replace default-zone `LocalDate.now()` / `LocalDateTime.now()` in financial services and entity construction with explicit business-time conversion.
- Decide whether exact events need `Instant`/`OffsetDateTime` or whether business `LocalDate` is sufficient. If both are needed, persist both with clear semantics.
- Make browser date defaults use a local Montevideo business-date helper or receive the date from the backend.
- Add deploy/runtime configuration checks for JVM, OS, and PostgreSQL timezone.

### Priority 2 — Align cancellation/reversal

- Decide refund/cancellation policy and apply the same predicate in every report.
- If reversals are required, preserve payment rows and mark them anulado with actor/time/reason; do not physically delete historical payments.
- Version or document migration behavior so old physical deletes and new logical reversals can be reconciled.

### Priority 3 — Improve state propagation

- Dispatch `FINANCIAL_DATA_CHANGED_EVENT` after successful debt payment, not only after debt deletion.
- Consider a server-driven invalidation/refetch policy for multiple tabs.
- Use stable composite movement IDs in every list that can contain a sale and its payment.

### Priority 4 — Harden schema and deployment

- Use an ordered migration mechanism with recorded versions/checksums, or otherwise make manual migration state auditable and fail-fast.
- Do not continue after a non-idempotent migration failure without an explicit, verified recovery decision.
- Add database checks or service-level reconciliation jobs for amount and relationship invariants.

### Priority 5 — Data repair process

- Run the SELECT-only diagnostics first and save results.
- Classify each candidate by source/history, not just by amount mismatch.
- Produce a dry-run repair report showing old/new values and the business decision behind each change.
- Obtain an independent backup and business approval before any mutation.

## 18. Regression Test Plan

### 18.1 Canonical financial invariants

1. Sale 1,000, initial 400, no later payment: Libro cash total is 400.
2. Same sale, later payment 600: Libro total remains 1,000, not 1,600.
3. Same sale, two later payments: sale row plus payment rows reconcile exactly once.
4. Partial payment, overpayment, zero, negative, and concurrent payments.
5. Retry with same idempotency key creates one payment only.
6. Cached debt fields, sale fields, and derived payment sum agree after create, payment, edit, delete/reversal.

### 18.2 Cancellation and reversals

1. Cancel unpaid sale.
2. Cancel sale after initial payment.
3. Cancel sale after later debt payments.
4. Confirm chosen rule produces identical inclusion in Libro, export, Dashboard, monthly summary, and statistics.
5. Reverse a payment and confirm immutable history, balance, and every report.

### 18.3 Time boundaries

1. Freeze clock at 20:59:59 Montevideo.
2. Freeze clock at 21:00:00 Montevideo.
3. Freeze clock at 23:59:59 Montevideo.
4. Freeze clock at 00:00:00 next day.
5. Run with JVM UTC and JVM Montevideo and assert business date remains correct under the selected design.
6. Query ranges at start-of-day, end-of-day, month boundary, and year boundary.
7. Browser outside Uruguay displays server-provided business dates correctly.

### 18.4 Endpoint consistency

For the same fixture, compare:

- `GET /ventas/libro`;
- `GET /ventas/libro/exportar`;
- `GET /ventas/resumen-mensual`;
- `GET /estadisticas/ingresos?periodo=dia/semana/mes`;
- Dashboard recent list and chart.

Assert movement IDs, dates, inclusion, counts, and totals—not just HTTP 200.

### 18.5 Frontend state

1. Register payment while Dashboard and Libro are mounted.
2. Verify both refetch without navigation.
3. Verify same behavior in a second tab or document the intentional limitation.
4. Verify a manual debt opened between 21:00 and midnight defaults to Montevideo date.
5. Verify sale and payment rows have unique stable keys.

### 18.6 Test environment

Current backend tests use embedded H2 for many integration tests, while production uses PostgreSQL. Add PostgreSQL integration coverage for timestamp/date semantics, constraints, partial indexes, native queries, and migration replay. Existing passing tests are not sufficient evidence for production timezone or reporting correctness.

## 19. Final Diagnosis

### 1. Can debts or payments appear one day later?

Yes. The mechanism is **HIGHLY PROBABLE** in production if the JVM/container defaults to UTC: financial writes call `LocalDate.now()`/`LocalDateTime.now()` without a zone, while the business zone is Montevideo. The manual debt form has a **CONFIRMED** UTC-date default defect. The actual production runtime zone remains unverified.

### 2. Why?

Because business dates are generated in the default runtime zone, stored without timezone metadata, and later compared/displayed using a mixture of Montevideo boundaries, browser-local parsing, and UTC `toISOString()` defaults. A late-evening Uruguay operation can cross the UTC calendar boundary.

### 3. Can a successful debt payment fail to appear in Libro?

It should create a `pago_deuda` row on the normal successful path. It can appear absent from a selected day because it was assigned the next date, be omitted when a channel filter is supplied, be temporarily stale in another mounted frontend surface, be excluded by another report’s stricter validity predicate, or be absent if a legacy/direct path changed balances without creating a payment row.

### 4. Can Home and Libro disagree?

Yes, **CONFIRMED**. Both can double count cumulative sale paid plus payment rows. They also use separate endpoints, ranges, filters, and frontend state. Monthly summary adds a second confirmed difference by excluding inactive/cancelled-related payments where Libro/Dashboard do not.

### 5. Is this mainly frontend, backend, timezone, or business logic?

The primary financial error is backend/business-model logic: duplicate representation of the same cash. The next-day symptom is backend/runtime timezone design, reinforced by a frontend UTC default. Stale Home/Libro values add a frontend refresh problem.

### 6. Is the payment transaction atomic?

Normally yes. `DeudaService.registrarPago` is transactional, locks the debt, writes the payment, recalculates debt and associated sale, and returns only after save. The issue is not a missing transaction write; it is inconsistent read semantics and timezone assignment.

### 7. Is there an independent transaction record?

No. `PagoDeuda` is the independent later-payment record. Libro entries are synthetic read-time DTOs derived from `Venta` and `PagoDeuda`.

### 8. Is cancellation behavior coherent?

No. The system marks linked debt inactive, retains payment rows, Libro/Dashboard can still count those rows, and monthly summary excludes them. Whether the retained payment should count as real income is unresolved, but the surfaces are not coherent.

### 9. Do current tests protect the critical invariant?

No. All observed tests pass, but existing statistics tests expect sale and payment amounts to be added separately, and key fixtures do not model the production recalculation that changes `venta.monto_pagado` cumulatively. There is no end-to-end assertion for the 1,000 = 400 + 600 scenario.

### 10. What should be fixed first?

First define and implement—after approval—a single canonical cash-movement model and reconcile existing data. In parallel, make `America/Montevideo` explicit at all financial write/read boundaries. Then align cancellation/reversal predicates, refresh events, migration governance, and regression coverage.

Overall status: **financial reporting correctness is not production-safe for debt-linked sales until the duplicate-movement invariant and timezone policy are resolved and existing data is reconciled.**
