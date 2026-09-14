# Sonograma Phase 2 — Financial Date and Timezone Fix

## 1 Root Cause

Financial dates were derived from several different clocks and interpretations:

- Backend `LocalDate.now()` / `LocalDateTime.now()` used the JVM default timezone.
- Some reporting code already used `America/Montevideo`, but through a separate, non-injectable implementation.
- The frontend manual-debt form used `new Date().toISOString().slice(0, 10)`, which derives a UTC calendar date rather than the Uruguay business date.
- Date-only fields, local business datetimes, and technical timestamps were not consistently distinguished.

At the Uruguay evening boundary, this could record September 14 as September 15 after UTC midnight, even though the operation was still performed on September 14 in Montevideo.

## 2 Canonical Business Time

The canonical business timezone is `America/Montevideo`.

Backend code now uses the injectable `BusinessTime` component, backed by a Spring `Clock` bean. The production clock reads the current instant with `Clock.systemUTC()`, and `BusinessTime` applies `America/Montevideo` when deriving the business `LocalDate` or `LocalDateTime`.

This keeps the source testable: tests can inject `Clock.fixed(...)` while production remains independent of the host/JVM default timezone.

The data semantics remain explicit:

- `LocalDate` fields are date-only business dates.
- `LocalDateTime` fields used for sales and pre-sale payment events are local business datetimes in Montevideo, matching the existing API/storage contract.
- Technical audit timestamps remain technical timestamps and were not broadly converted as part of this financial-only phase.

## 3 Backend Changes

- Added `BusinessTimeConfig` and `BusinessTime` as the reusable business-time source.
- Sales without an explicit `fechaVenta` now default to Montevideo business time.
- Debt creation, sale-to-debt synchronization, debt default dates, `fechaPago`, and `fechaUltimoPago` now use Montevideo business time when generated.
- Explicit debt, sale, historical, and operator-entered dates continue to be preserved.
- Excel debt-import fallback dates now use Montevideo business date.
- Pre-sale fallback creation dates and the pre-sale-to-sale payment datetime now use Montevideo business time.
- Store expense fallback dates and current-month summary boundaries now use Montevideo business date.
- Dashboard income-series and monthly financial-summary current-period calculations now use the same source.
- Existing Libro/Dashboard payment date conversion continues to use the stored date-only payment date at start of day; no API format changed.

Unrelated security, catalog, CRM, shipping, import-job, and technical audit timestamps were left outside this phase.

## 4 Frontend Changes

Added `businessDateInMontevideo`, a centralized browser helper based on `Intl.DateTimeFormat` with an explicit `America/Montevideo` timezone.

The helper is used by:

- Manual debt defaults and fallback form values.
- Pre-sale creation date defaults.
- Store-expense date defaults.
- Libro period/date defaults.

The manual-debt UTC `toISOString()` strategy was removed. The API payload formats remain unchanged; the frontend still sends `YYYY-MM-DD` for date-only fields.

## 5 Database Impact

No migration was added.

No historical rows were reinterpreted, rewritten, or repaired. Existing PostgreSQL `DATE` and `TIMESTAMP WITHOUT TIME ZONE` columns remain unchanged. Only newly generated financial values and current-period calculations use the canonical business-time rules.

## 6 Boundary Examples

With the canonical Montevideo timezone:

| Uruguay business time | UTC instant | Business date |
|---|---:|---:|
| Sep 14 10:00 | Sep 14 13:00Z | Sep 14 |
| Sep 14 21:30 | Sep 15 00:30Z | Sep 14 |
| Sep 14 23:59:59 | Sep 15 02:59:59Z | Sep 14 |
| Sep 15 00:00 | Sep 15 03:00Z | Sep 15 |
| Sep 30 23:59 | Oct 1 02:59Z | Sep 30 |
| Oct 1 00:00 | Oct 1 03:00Z | Oct 1 |
| Dec 31 23:59 | Jan 1 02:59Z | Dec 31 |
| Jan 1 00:00 | Jan 1 03:00Z | Jan 1 |

Therefore, a debt payment made at September 14 21:30 or 23:59:59 in Uruguay receives `fechaPago = 2026-09-14`; a payment at September 15 00:00 receives `fechaPago = 2026-09-15`.

## 7 Phase 1 Compatibility

The Phase 1 income correction remains in place. `IngresoLibroCalculator` continues to be the shared income rule for Libro, Dashboard/statistics, and the monthly summary:

- A sale-linked debt uses `montoPagadoInicial` for the original sale movement.
- Later debt payments remain separate payment movements.
- Sales without debt retain their existing paid-amount behavior.

Phase 2 only supplied the canonical business time and changed financial date generation/report-period boundaries. It did not revert or replace the Phase 1 movement logic.

## 8 Tests Added or Updated

- Added fixed-clock `BusinessTimeTest` covering September 14 evening, UTC rollover, month-end, and year-end boundaries, including a clock whose configured zone differs from Montevideo.
- Updated sale-service coverage to verify a sale defaulting at a fixed UTC instant is recorded as the corresponding Montevideo datetime.
- Updated debt-service coverage to verify payments at a UTC-next-day instant receive the Montevideo date.
- Updated direct-construction tests for the new injectable business-time dependency.
- Added frontend helper tests for September 14 21:30/23:59-equivalent UTC instants, midnight rollover, month-end, and year-end.
- Ran the focused backend date/payment tests with `-Duser.timezone=UTC`.
- Ran the frontend business-date test with `TZ=UTC`.

## 9 Test Results

- Focused backend Phase 2 set: passed.
- Full backend Maven suite: 378 tests executed, 0 failures, 0 errors, 1 skipped.
- Full frontend Vitest suite: 19 files, 135 tests passed.
- Frontend ESLint: passed.
- UTC-environment backend boundary set: passed.
- UTC-environment frontend business-date test: 2 tests passed.

## 10 Remaining Risks

- Existing historical data may contain dates already affected by the former timezone behavior; this phase intentionally does not reinterpret or repair those rows.
- Technical `TIMESTAMP WITHOUT TIME ZONE` audit fields in unrelated modules still follow their existing implementations; they are not financial business dates.
- Stored local business datetimes intentionally retain the current API/database contract. Any future migration to offset-aware timestamps would require a separate compatibility and data-migration decision.
- The production container/server clock must still provide a correct current instant; the business-date calculation is protected from its default timezone, not from an incorrect system clock.

## 11 Final Verification

1. Is `America/Montevideo` the single canonical source for generated financial business dates? **Yes.**
2. Can the business-time source be fixed deterministically in tests? **Yes, through injectable `Clock`.**
3. Are sale, debt, payment, pre-sale, expense, import fallback, and financial report-period defaults covered? **Yes.**
4. Are explicit operator-entered and historical dates preserved? **Yes.**
5. Are Libro, Dashboard/statistics, and monthly-summary financial movements still compatible with Phase 1? **Yes.**
6. Were migrations, historical data repair, cancellation-rule changes, frontend refresh/event changes, unrelated modules, and deployment changes excluded? **Yes.**
