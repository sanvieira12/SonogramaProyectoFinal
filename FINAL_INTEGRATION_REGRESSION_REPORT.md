# FINAL INTEGRATION / REGRESSION REPORT

Date: 2026-10-07
Scope: Libro de Ventas + Catalog + physical-copy lifecycle + Stock valuation

## 1. Executive Summary

The Phase A-E behavior is coherent across the tested business sequences. AVAILABLE physical rows remain the operational inventory authority; catalog aggregates and Stock valuation follow those rows through exact sale, cancellation, and retained removal. Libro de Ventas retains the approved formulas and refresh semantics.

No application regression was found. No production code was changed in this final phase. The only code changes made here are integration/regression coverage and stronger import-to-Stock assertions. No migration was created and nothing was deployed.

Verification result: 266 backend regression tests and 144 frontend regression tests passed, targeted ESLint passed, both production packages built, and `git diff --check` passed.

## 2. Scope

This audit covered:

- Libro de Ventas formulas, applied-period refreshes, and stale-response protection.
- Catalog presentation and aggregate quantity synchronization.
- Physical-copy availability, QR identity, exact sale selection, cancellation, and retained removal.
- NEW and USED Stock valuation, currencies, missing-value disclosure, and global-card semantics.
- Discogs Excel, manual Discogs, and VinylFuture receipt paths.
- Legacy cancellation safe failure.
- Dashboard semantics by inspection only.

The previously supplied audit/change reports were reviewed before verification. Existing unrelated and Phase A-E working-tree changes were preserved.

## 3. Libro de Ventas Verification

**Approved balance formula:** confirmed. `ResumenFinancieroMensualService` still computes:

`Balance final = Ingresos registrados - Gastos totales`

The backend financial regression suites confirm the existing definitions of Ingresos registrados, Total ventas, Ganancia bruta, Gastos, month boundaries, debt behavior, and pre-sale behavior. None of those formulas was changed in this phase.

Successful mutations call the shared applied-period refresh, which reloads the table and summary cards together. Frontend regression coverage confirms this for:

- sale cancellation;
- debt-payment annulment;
- debt-payment edit;
- sale edit;
- pre-sale payment edit/deletion;
- an applied month that differs from the currently edited month.

Failed cancellation does not replace the current cards with speculative data. Older table or summary responses cannot overwrite the newer applied period.

Explicit answers:

- Is Libro de Ventas still using the approved Balance formula? **Yes.**
- Do Libro cards refresh after supported financial mutations? **Yes.**
- Were any unrequested business formulas changed? **No.**

## 4. Inventory Authority Verification

For modeled inventory, the verified invariant is:

`available quantity = COUNT(DiscoQrCopy WHERE estado = DISPONIBLE)`

Supported mutations resynchronize `Disco.cantidadCopias` to that count and derive the parent state from the result. Exact sale, cancellation, NEW decrement, USED retained removal, and receipt/import coverage all assert this behavior. SOLD and REMOVED rows remain historical rows but are not counted as available.

Explicit answer: Are AVAILABLE physical rows the operational stock authority? **Yes, for modeled inventory.**

## 5. NEW Sell / Cancel Scenario

The new end-to-end integration test creates a NEW product with a UYU 2,000 sale price, EUR 10 acquisition cost, and three unique AVAILABLE physical QRs.

Verified initial state:

- Catalog quantity: 3.
- Three distinct QR identities.
- Imported NEW: EUR 30.
- Projected NEW: UYU 6,000.

After selling the explicitly selected second copy:

- that exact row is VENDIDO and retains its QR;
- its physical ID is persisted in the sale snapshot;
- the other two rows remain AVAILABLE;
- Catalog quantity is 2;
- imported NEW is EUR 20;
- projected NEW is UYU 4,000;
- the sale appears in Libro de Ventas;
- the sold QR cannot be sold again.

After cancellation:

- the same exact physical row returns to AVAILABLE;
- its QR is unchanged;
- Catalog quantity returns to 3;
- imported NEW returns to EUR 30;
- projected NEW returns to UYU 6,000;
- the cancelled sale is excluded from the active Libro result under existing semantics;
- the frontend refreshes the applied-period table and cards.

No QR was regenerated or substituted.

## 6. NEW Decrement Scenario

The same controlled product was reduced 3 -> 2 -> 1 -> 0 through the supported quantity mutation.

At every step:

- exactly one additional AVAILABLE physical row became REMOVED;
- no physical row was deleted;
- all original QR values remained persisted;
- `Disco.cantidadCopias` equaled the remaining AVAILABLE count;
- imported NEW fell by EUR 10 per removed copy;
- projected NEW fell by UYU 2,000 per removed copy;
- the removed QR was rejected by the sale path.

At zero AVAILABLE rows, all three physical rows still exist as REMOVED, both valuation totals are zero, and the parent state is SIN_STOCK.

Explicit answer: Does NEW projected value react correctly to quantity changes? **Yes.**

## 7. USED Sell / Cancel Scenario

A controlled USED product was verified with two exact copies:

- Copy 1: UYU 700, VG, unique QR.
- Copy 2: UYU 900, NM, unique QR.

Initially, Catalog exposes two available copies and the UYU 700-900 commercial range; Stock projects UYU 1,600. NEW imported value is unaffected.

Selling Copy 2 changes that exact row to SOLD while preserving its QR, UYU 900 price, and NM condition. Catalog quantity becomes 1, the remaining commercial summary is UYU 700 / VG, and projected USED becomes UYU 700.

Cancellation restores the same row and QR to AVAILABLE without changing price or condition. Quantity returns to 2, the range returns to UYU 700-900, and projected USED returns to UYU 1,600.

## 8. USED Retained Removal Scenario

The supported retained-removal operation was run against the UYU 900 / NM copy.

Verified results:

- state changed to REMOVED;
- database row and QR remained persisted;
- exact price and condition remained persisted;
- manual Discogs batch provenance remained persisted;
- disposition reason, note, actor, and timestamp were recorded;
- Catalog quantity became 1 and its summary reflected UYU 700 / VG;
- projected USED became UYU 700;
- sale search exposed only the remaining AVAILABLE copy;
- the removed QR was rejected by sale registration.

No hard delete occurred.

## 9. USED Unknown-Price Scenario

Coverage includes an AVAILABLE UYU 700 copy and AVAILABLE copies with a null exact price.

Verified behavior:

- Stock sums only the known UYU 700 amount;
- null-price copies are counted in `usedAvailableCopiesWithoutPrice`;
- parent product price is never substituted into exact USED valuation;
- selling a null-price exact copy does not write the sale-line amount back to its physical row;
- cancellation restores the same row with its exact price still null;
- retained removal keeps its exact price null.

Explicit answers:

- Does USED projected value use exact available-copy prices? **Yes.**
- Are unknown USED prices still excluded and disclosed? **Yes.**

## 10. Exact USED Selection

The sale-search and service suites verify all supported selection modes:

- one ordinary AVAILABLE USED physical row can be auto-selected;
- multiple AVAILABLE USED rows require explicit physical selection;
- manual Discogs selection preserves exact source/copy behavior;
- Discogs Excel receipts create exact physical identity used by sale search;
- an exact copy price is authoritative when present;
- SOLD and REMOVED candidates are unavailable.

Explicit answer: Does every modeled USED sale resolve an exact physical copy? **Yes.** Manual sale lines without modeled inventory remain a separate existing concept.

## 11. QR Lifecycle Matrix

| Physical state | Sale behavior | Identity behavior |
|---|---|---|
| AVAILABLE | Exact row may be sold | Existing row and QR are used |
| SOLD | Rejected | Row and QR remain historical |
| REMOVED | Rejected | Row and QR remain historical |
| Cancelled sale | Saleable only after the snapshotted row is restored to AVAILABLE | Same row and same QR; no regeneration |

The parent `Disco.codigoQr` compatibility value does not override exact `DiscoQrCopy` identity.

Explicit answer: Are QR identities preserved across sale/cancel/removal? **Yes.**

## 12. Legacy Cancellation

The legacy safe-failure test covers a sale with physical rows but no `copyIdsSnapshot`. If the exact sold row cannot be proven, cancellation fails before aggregate quantity or sale state is mutated.

The system does not invent an identity and does not perform a standalone `Disco.cantidadCopias + 1`.

Explicit answer: Can a legacy cancellation create aggregate-only stock? **No.**

## 13. Discogs Excel Verification

Discogs Excel coverage confirms that an AVAILABLE USED receipt creates AVAILABLE physical rows and synchronizes the aggregate. Supplied UYU 700 / VG and UYU 900 / NM values remain attached to their respective rows.

The final integration assertion additionally proves that those two available exact prices contribute UYU 1,600 to projected USED, with two available USED copies and no missing-price count. NEW imported subtotals remain unchanged. Existing replay, sold-row, SIN PRECIO, identity, and concurrency protections also passed.

## 14. Manual Discogs Verification

Manual Discogs coverage confirms:

- exact-once operation behavior and idempotent replay;
- normalized source/batch lineage;
- exact physical QR identity;
- submitted UYU 1,500 price and physical condition retained on the copy;
- aggregate equals the AVAILABLE physical count;
- sale search and exact sale behavior remain available;
- finalization does not mutate physical-copy fields.

The final integration assertion proves the AVAILABLE copy contributes UYU 1,500 to projected USED and does not affect imported NEW EUR or UYU totals.

## 15. VinylFuture Verification

The VinylFuture regression suites confirm that NEW receipts:

- create the requested number of AVAILABLE physical rows with unique identities;
- add to existing modeled stock from the AVAILABLE-row base;
- retain product-level NEW price and authoritative acquisition cost/currency;
- do not mutate stock during preview;
- remain idempotent on retry/double submission;
- avoid silent merges for ambiguous supplier identity.

Stock valuation tests independently prove that each resulting AVAILABLE NEW row multiplies the same product-level acquisition cost and sale price, while sale/removal/cancellation changes the available-row multiplier. No import semantics were redesigned.

## 16. Stock Currency Verification

Verified Stock rules:

- NEW EUR costs contribute only to `importedNewEur`.
- NEW UYU costs contribute only to `importedNewUyu`.
- Mixed currency totals remain separate and are not converted.
- The current EUR/UYU pricing rate is not an input to actual imported subtotals.
- Shipping is not an input to Stock valuation.
- Unsaved pricing settings and preview results do not alter actual Stock cards.
- A persisted NEW sale-price change followed by authoritative refresh changes projected NEW value.
- Missing/non-positive price or cost and unknown acquisition currency are disclosed by counters rather than invented.

Explicit answers:

- Is imported NEW value separated by EUR/UYU? **Yes.**
- Is shipping excluded? **Yes.**
- Do unsaved pricing settings affect actual Stock valuation? **No.**

## 17. Stock Global Semantics

Stock valuation is fetched independently of the table search and NEW/USED filters. Frontend tests confirm those controls alter only visible rows/result counts; global cards do not change or refetch as filtered subtotals.

The backend valuation service loads only AVAILABLE physical rows. Products with zero AVAILABLE rows contribute zero. SOLD and REMOVED rows contribute neither imported NEW nor projected NEW/USED value.

Explicit answer: Can SOLD or REMOVED copies contribute to Stock valuation? **No.**

## 18. Refresh / Race-Condition Verification

The following last-request-wins protections were inspected and regression-tested:

- Catalog physical-copy detail ignores an older response after a newer authoritative refresh.
- Stock valuation ignores an older response after a newer inventory refresh.
- Pricing preview ignores stale/out-of-order work.
- Libro table and summary each use request identities so an older period cannot overwrite the current applied period.

Libro's successful financial mutation path reloads table and summary concurrently for the same applied period. The React review also confirmed stable callbacks and contained effect/listener behavior; no frontend production correction was required.

## 19. Dashboard Audit

Dashboard currently computes **Disponibles** by filtering parent products whose `Disco.estado` is DISPONIBLE and summing the parent DTO's `cantidadCopias`. Its displayed **en stock** value multiplies parent `precioVenta * cantidadCopias` across all such products.

That differs from authoritative Stock because Dashboard:

- uses parent product state/aggregate instead of directly selecting AVAILABLE physical rows;
- combines NEW and USED into one sale-price total;
- uses parent price for USED instead of exact copy prices;
- does not disclose USED copies missing exact price;
- does not expose imported NEW cost split into EUR and UYU;
- does not expose missing acquisition cost/currency counters.

Recommendation: **A — keep it temporarily with clearer naming.** Rename it in a later authorized change to make clear that it is a catalog-level availability/value indicator, and direct financial inventory decisions to Stock. A later product decision may adopt multiple metrics, but no Dashboard change was made here.

## 20. Production Data Diagnostic

PRODUCTION DATA DIAGNOSTIC: NOT EXECUTED

The current environment did not provide a reachable current/production database: the configured local PostgreSQL endpoint did not respond, no applicable database environment connection was present, and no local container runtime connection was available. No diagnostic counts are reported, no data was mutated, and no repair migration was created.

Explicit answer: Were production-data anomalies found? **Not determinable because the production-data diagnostic could not be executed. No counts were fabricated.**

## 21. Regressions Found

No Phase A-E application regression or cross-module inconsistency was found in the executed matrix.

During construction of the final NEW zero-stock scenario, the initial test expected a narrower conflict subtype. The application correctly rejected the sale earlier through the existing general `NegocioException` zero-stock guard. The test expectation was aligned with that valid behavior; this was not a production defect and required no application change.

## 22. Fixes Applied During Final Integration

No production fix was required or applied.

Test-only additions:

- Added four complete business-sequence integration tests covering NEW sale/cancel/decrement, USED exact sale/cancel, USED retained removal, and USED unknown-price behavior across Catalog, physical rows, QR, Libro, and Stock.
- Added Discogs Excel receipt assertions tying exact copy price/condition to global Stock valuation.
- Added manual Discogs receipt assertions tying exact copy price/provenance to global Stock valuation.

No business formula was changed.

## 23. Files Modified

Files changed specifically during this final integration phase:

- `sonograma-backend/src/test/java/com/sonograma/service/FinalInventoryValuationIntegrationTest.java` (new).
- `sonograma-backend/src/test/java/com/sonograma/service/importacion/DiscogsImportJobServiceTest.java` (additional Stock assertions).
- `sonograma-backend/src/test/java/com/sonograma/service/importacion/ManualDiscogsReceiptOperationServiceTest.java` (additional Stock assertions).
- `FINAL_INTEGRATION_REGRESSION_REPORT.md` (this report).

No production source file was changed in this phase. Existing Phase A-E and unrelated working-tree content was not reverted or overwritten.

## 24. Tests / Build Results

| Verification | Result |
|---|---|
| Focused new/import/Stock backend tests | 66 passed; 0 failed; 0 errors; 0 skipped |
| Complete relevant backend matrix | 266 passed; 0 failed; 0 errors; 0 skipped |
| Frontend regression matrix | 7 files, 144 tests passed |
| ESLint on relevant modified frontend/API files | Passed with no findings |
| Frontend production build (`vite build`) | Passed |
| Backend package (`mvn package -DskipTests`) | Passed |
| `git diff --check` | Passed |
| Migration check | No migration created |
| Deployment | Not performed |

The backend matrix included Libro summary/reporting, debts, physical-copy services/read models, exact sale selection, permanent-deletion boundaries, NEW/USED lifecycle integration, all three receipt/import sources, idempotency, QR characterization, and Stock valuation.

The frontend matrix included Pricing/Stock, Catalog, Nueva Venta, Libro de Ventas, PreVentas, Deudas, and the API client.

## 25. Known Remaining Risks

- Current production data could not be measured for historical drift or missing physical/commercial fields.
- Legacy products without physical-copy rows remain compatibility data and are not made authoritative by this phase.
- Historical modeled sales without `copyIdsSnapshot` intentionally fail cancellation when exact identity cannot be proven; they require an explicit audited remediation decision, not automatic inference.
- `copyIdsSnapshot` remains historical serialized identity rather than a database foreign-key relationship.
- Reservation/pre-sale inventory semantics still include pre-existing parent-level behavior outside this phase's authorization.
- Null USED prices remain intentionally excluded and disclosed; operational cleanup is still needed where exact valuation is desired.
- NEW acquisition cost is product-level, so all physical copies of a NEW product share that unit cost under the current model.
- Dashboard remains a catalog-level approximation and must not be read as the authoritative Stock valuation.
- Administrative permanent deletion remains a separate privileged boundary from retained operational removal.
- Parent QR remains a compatibility alias; exact physical QR is authoritative for modeled sale/cancellation flows.

These are known limitations or explicit safety policies, not newly discovered regressions.

## 26. Final Go / No-Go Recommendation

**GO for integration of the current code and test changes.** The verified code paths satisfy the requested cross-module invariants, and all required local checks passed.

This GO does not authorize deployment. Before a production rollout, run the read-only production-data diagnostic against the actual database and review any anomaly counts. No migration or automated repair should be introduced without separate authorization.

Explicit final answers:

- Approved Libro balance formula retained: **Yes**.
- Libro cards refresh after supported mutations: **Yes**.
- AVAILABLE physical rows are modeled stock authority: **Yes**.
- SOLD/REMOVED contribute to valuation: **No**.
- Modeled USED sales resolve exact physical identity: **Yes**.
- Legacy cancellation can create aggregate-only stock: **No**.
- QR identity preserved: **Yes**.
- NEW projected value follows quantity: **Yes**.
- USED projected value uses exact available-copy prices: **Yes**.
- Unknown USED prices excluded and disclosed: **Yes**.
- Imported NEW separated into EUR/UYU: **Yes**.
- Shipping excluded: **Yes**.
- Unsaved pricing settings alter actual valuation: **No**.
- Production-data anomalies found: **Not assessed; diagnostic unavailable**.
- Unrequested formulas changed: **No**.
- Migration created: **No**.
- Anything deployed: **No**.

FINAL STATUS: GO
