# Stock Valuation Change Report

## 1. Executive Summary

Phase E replaces the Stock screen's former catalog-preview total with three backend-authoritative, global inventory metrics:

- **Valor total importado — Nuevos:** current acquisition value of available NEW physical copies, shown as separate EUR and UYU subtotals.
- **Valor proyectado — Nuevos:** persisted current product sale price multiplied by the number of available NEW physical copies.
- **Valor proyectado — Usados:** sum of known exact prices on available USED physical copies.

Only `DiscoQrCopy` rows in `DISPONIBLE` state contribute. SOLD and REMOVED rows do not. The implementation adds a read-only `GET /pricing/stock-valuation` endpoint, explicit data-quality counts, authoritative refresh behavior, and stale-response protection. No migration or deployment was performed.

## 2. Previous Stock Formula

The previous card labeled `Valor total proyectado` reduced the pricing-preview rows in the browser:

```text
SUM(Number(row.finalSalePriceUyu || 0))
```

That was one simulated unit price per product row. It did not multiply by available physical-copy quantity, did not use exact USED copy prices, could change with unsaved pricing settings, and mixed NEW and USED under one headline. Its aggregation was also a frontend JavaScript-number calculation rather than one authoritative backend valuation.

## 3. Business Rules Implemented

The implementation applies these rules:

1. Physical `DISPONIBLE` rows define current inventory.
2. Imported value includes NEW inventory only.
3. NEW acquisition value uses persisted unit `Disco.costo` and its persisted `Disco.costoMoneda`.
4. NEW projected value uses persisted `Disco.precioVenta` per available copy.
5. USED projected value uses each available copy's exact `DiscoQrCopy.precioVenta`.
6. Unknown values are excluded and counted, never guessed.
7. EUR and UYU acquisition costs remain separate.
8. Shipping, pricing extras, markup, SOLD copies, REMOVED copies, and unsaved preview settings do not contribute.

## 4. Inventory Authority

The service loads active, non-soft-deleted `Disco` products and then batch-loads their `DiscoQrCopy` rows whose state is exactly `DISPONIBLE`. It groups those rows by product and values only those rows.

`Disco.cantidadCopias`, retained total-copy count, and parent `Disco.estado` are not quantity authorities for this valuation. A historical product row with zero available physical copies contributes zero. This preserves the Phase D authority and tolerates legacy aggregate-count drift without valuing unavailable stock.

## 5. NEW Projected Value

For each NEW product:

```text
contribution = persisted Disco.precioVenta × count(available physical copies)
```

The price is the current product/catalog commercial authority established in Phase A; no per-copy NEW price is required or introduced. A missing, zero, or invalid non-positive sale price contributes zero and increments `newAvailableCopiesWithoutSalePrice` by the affected available-copy count.

The focused lifecycle test verifies UYU 2,000 × 3 available = UYU 6,000, sale to two available = UYU 4,000, cancellation back to three = UYU 6,000, and subsequent REMOVED state back to UYU 4,000. Pre-existing SOLD and REMOVED rows contribute zero.

## 6. USED Projected Value

For USED products:

```text
ProjectedUsedKnown = SUM(DiscoQrCopy.precioVenta)
for exact copies whose state is DISPONIBLE and whose price is positive
```

The parent `Disco.precioVenta` is never used as a fallback and is never multiplied by USED quantity. Tests verify 700 + 900 available = 1,600 and, separately, 700 available + 900 SOLD + 1,100 REMOVED = 700.

## 7. Unknown USED Price Handling

An available USED copy with a null, zero, or otherwise non-positive exact price is excluded from the known projected amount. It increments `usedAvailableCopiesWithoutPrice`.

The UI shows the known amount together with an amber warning such as `1 copia disponible sin precio específico, excluida del valor`. Therefore the visible number is not presented as silently complete when exact prices are missing. No parent-price fallback or historical price invention occurs.

## 8. NEW Imported Value

`Valor total importado — Nuevos` includes exactly:

```text
persisted positive Disco.costo × available NEW physical-copy count
```

Only active NEW products with available physical rows contribute. USED records are excluded even when they have a parent cost. SOLD and REMOVED copies are excluded. Missing/non-positive costs and costs with unrecognized currency are excluded and surfaced through diagnostic counts.

## 9. Acquisition Cost Authority

The authoritative unit acquisition amount is the persisted product-level `Disco.costo`. This matches the existing VinylFuture receipt/import behavior: the product stores the invoice/page base purchase price and persists `costoMoneda = EUR`. Pricing extras are calculated separately by `CatalogPricingService`; they are not written into `Disco.costo`.

The model does not store acquisition cost on each NEW `DiscoQrCopy`. Phase E therefore does not invent copy-level cost. It multiplies the product-level unit cost by available copies only for the existing equivalent-copy NEW model. This assumption is called out as a future risk if heterogeneous acquisition batches are ever merged into one product.

## 10. Currency Handling

The imported-value card displays two mathematically separate subtotals:

- `EUR <amount>` for rows whose trimmed, case-normalized `costoMoneda` is `EUR`;
- `UYU $<amount>` for rows whose trimmed, case-normalized `costoMoneda` is `UYU`.

The implementation does not infer currency from a positive number, does not treat UYU as EUR, and does not add EUR directly to UYU. A positive cost with null or unrecognized currency is excluded and counted in `newAvailableCopiesWithUnknownAcquisitionCurrency`.

The current configured EUR/UYU rate does **not** affect either imported subtotal. No current-rate value is presented as historical UYU spend, and no historical exchange rate is invented.

## 11. Shipping / Extras Treatment

Shipping is not included. Fixed pricing extras, supplier shipping metadata, markup, and computed `realUnitCostEur` are also not included.

Those fields remain valid inputs to pricing simulation and automatic sale-price calculation, but the requested imported-value headline reads only the persisted base acquisition cost of the records. The Stock UI states `Costos unitarios persistidos · sin envío ni conversión entre monedas`.

## 12. Pricing Preview vs Actual Stock

The pricing table remains a simulation driven by its settings form. The three actual Stock headline metrics come from the independent read-only valuation endpoint and persisted inventory data.

Changing the exchange rate, extras, or markup in an unsaved form—and clicking `Actualizar vista previa`—does not mutate or refetch the actual valuation. Persisting/applying pricing changes refreshes valuation because those actions can change authoritative `Disco.precioVenta`. The old preview-derived total was removed.

## 13. UI Changes

The Stock screen now renders three distinct cards in its existing visual language:

- `Valor total importado — Nuevos`, with EUR and UYU subtotals plus missing-cost/currency warnings;
- `Valor proyectado — Nuevos`, with available NEW count and missing-sale-price warning;
- `Valor proyectado — Usados`, with available USED count and exact-price exclusion warning.

The table's preview-row count and AUTO/MANUAL counts remain secondary cards. No profit, margin, expected-profit, or imported-versus-projected difference was added.

## 14. Global vs Filtered Totals

Headline metrics remain global inventory metrics. Search, NEW/USED filtering, and supplier sorting affect only the pricing-preview table and do not change the three valuation cards. A frontend regression test changes both search and condition filter and confirms the global values and endpoint call count remain unchanged.

## 15. Refresh Strategy

The Stock page loads valuation authoritatively on every mount, covering return visits after sale, import/receipt, quantity changes, or retained removal. While mounted it also listens to the existing `sonograma:financial-data-changed` event, which covers in-app persisted financial/inventory mutations that emit the shared event. Persisted pricing apply/reset/markup actions explicitly reload valuation.

No polling was added. A monotonically increasing request id prevents an older async response from overwriting a newer result; unmount invalidates the pending request and removes the event listener. The same stale-response protection was also added to pricing preview requests.

## 16. Dashboard Comparison

Dashboard was audited and intentionally left unchanged for final integration. Its `Disponibles` card currently:

- filters parent product DTOs by `d.estado === DISPONIBLE`;
- sums DTO `cantidadCopias` for its count;
- computes one combined UYU `en stock` amount as `Disco.precioVenta × cantidadCopias`;
- does not use exact per-copy USED prices or display unknown USED-price exclusions;
- does not expose imported NEW acquisition value.

That formula is not semantically identical to any one of the new Stock metrics. A small safe alignment would require deciding which of the three meanings Dashboard should show, so redesign was not assumed in Phase E. The discrepancy is explicit and remains a final-integration decision.

## 17. Legacy/Data Risks

- Production data was not queried or rewritten; historical aggregate/physical drift may still exist.
- Available NEW copies with missing/non-positive `Disco.costo` are excluded and counted.
- Positive NEW costs with missing/unrecognized `costoMoneda` are excluded and counted.
- Available NEW copies with missing/non-positive `Disco.precioVenta` are excluded and counted.
- Available USED copies with missing/non-positive exact `DiscoQrCopy.precioVenta` are excluded and counted.
- Product-level NEW cost assumes equivalent unit acquisition cost. The current model cannot faithfully represent different receipt costs for different NEW copies merged under one product.
- The separate pricing-preview table retains its own legacy simulation semantics; its values must not be interpreted as actual inventory valuation.
- The valuation is correct against modeled physical rows, but no production reconciliation or destructive repair was attempted.

## 18. Phase A-D Regression

Phase A remains intact: NEW commercial presentation is product-level and physical QR identities stay unique.

Phase B remains intact: USED commercial authority is copy-specific and null historical prices are not invented.

Phase C remains intact: retained USED removal and REMOVED terminality are unchanged.

Phase D remains intact: available physical rows remain inventory authority; exact USED sale selection, cancellation restoration, retained decrement, and QR lifecycle behavior are unchanged. The backend regression selection exercised catalog pricing, copy services, state transitions, permanent removal, sale search/read models, exact-copy sales, imports, VinylFuture stock/receipt behavior, debts, and financial consistency.

## 19. Libro de Ventas Regression

Phase E did not modify Libro de Ventas source or financial formulas. In particular, it did not change Balance final, Ingresos registrados, Ganancia bruta, Gastos, monthly-summary arithmetic, applied-period refresh behavior, or sale/debt/pre-sale formulas.

The existing workspace already contains earlier approved Libro de Ventas work from prior phases; Phase E leaves it untouched. The focused frontend `LibroVentas` tests and backend monthly/financial consistency tests passed as regressions.

## 20. Files Modified

Phase E added:

- `sonograma-backend/src/main/java/com/sonograma/dto/StockValuationDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/service/StockValuationService.java`
- `sonograma-backend/src/test/java/com/sonograma/service/StockValuationServiceTest.java`
- `STOCK_VALUATION_CHANGE_REPORT.md`

Phase E updated:

- `sonograma-backend/src/main/java/com/sonograma/controller/PricingController.java`
- `sonograma-backend/src/main/java/com/sonograma/repository/DiscoQrCopyRepository.java`
- `sonograma-backend/src/test/java/com/sonograma/service/ExactCopySaleIntegrationTest.java`
- `frontend/src/api/sonograma.js`
- `frontend/src/api/sonograma.test.js`
- `frontend/src/pages/PricingSettingsPage.jsx`
- `frontend/src/pages/PricingSettingsPage.test.jsx`

Other dirty-worktree files belong to completed earlier phases or pre-existing user work and were preserved.

## 21. Tests Added/Updated

Backend valuation coverage includes:

- NEW 2,000 × 3 available = 6,000;
- NEW after exact sale = 4,000;
- NEW after cancellation = 6,000;
- NEW after removal = 4,000;
- pre-existing SOLD/REMOVED NEW rows excluded;
- USED 700 + 900 available = 1,600;
- USED 700 available with 900 SOLD and 1,100 REMOVED = 700;
- available USED null exact price counted and parent price not used;
- USED excluded from imported value;
- EUR and UYU NEW costs aggregated separately;
- missing cost, unknown currency, and missing NEW sale price counted;
- exact-copy sale/cancel/decrement integration recalculates 1,000 → 0 → 1,000 → 0.

Frontend coverage includes:

- API route and response handling;
- three distinct cards and separate currency display;
- visible missing-data warnings;
- global totals unaffected by search/filter;
- unsaved preview settings do not affect actual valuation;
- shared-event authoritative reload;
- stale out-of-order valuation response rejection.

Shipping/current-rate exclusion is additionally enforced structurally: the valuation service depends only on product and physical-copy repositories and reads only persisted `costo`, `costoMoneda`, `precioVenta`, condition, and copy state/price. It has no pricing-settings, order, shipping, or pricing-preview dependency.

## 22. Test/Build Results

- Focused backend `StockValuationServiceTest`: **4 passed**.
- Selected backend Stock/pricing/lifecycle/import/currency/financial regression suite: **262 passed, 0 failures, 0 errors**.
- Focused frontend Stock/API suite: **52 passed** across 2 files.
- Frontend Stock/Catalog/Nueva Venta/Libro/API regression suite: **132 passed** across 5 files.
- ESLint on modified frontend files: **passed**.
- Frontend production build: **passed**.
- Backend package with tests skipped: **passed**.
- `git diff --check`: **passed** before report creation and will be rerun after it.

## 23. Remaining Issues Before Final Integration

1. Decide which valuation concept, if any, should replace Dashboard's current combined `en stock` amount.
2. Reconcile production-only legacy anomalies with a read-only diagnostic before any separately authorized repair.
3. Review and resolve the surfaced missing cost, currency, NEW price, and exact USED price counts in production data.
4. If future receipts can merge NEW copies acquired at different unit costs into one product, extend the model with receipt/copy-level acquisition authority before claiming exact per-copy invested value.
5. Keep the pricing-preview simulation visually and conceptually separate from authoritative valuation during final integration.

Explicit answers:

- **What is included in Valor total importado?** Positive persisted unit `Disco.costo` multiplied by current `DISPONIBLE` physical-copy count for NEW products only, separated by known persisted currency.
- **Are USED records included?** No.
- **Is shipping included?** No.
- **What currencies are displayed?** EUR and UYU as separate subtotals.
- **How are EUR and UYU distinguished?** By trimmed, case-normalized `Disco.costoMoneda`; never by amount sign or source inference.
- **Does the current exchange rate affect the number?** No.
- **What is included in NEW projected value?** Persisted product `Disco.precioVenta` multiplied by available NEW physical-copy count.
- **What is included in USED projected value?** Known positive exact `DiscoQrCopy.precioVenta` values for available USED copies only.
- **How are unknown USED prices represented?** Excluded from the known amount and shown as a separate available-copy warning count.
- **Do SOLD copies contribute?** No.
- **Do REMOVED copies contribute?** No.
- **Do draft pricing settings change actual Stock valuation?** No.
- **Did any Libro de Ventas formula change?** No.
- **Was any migration created?** No.
- **Was anything deployed?** No.

STATUS: PASS
