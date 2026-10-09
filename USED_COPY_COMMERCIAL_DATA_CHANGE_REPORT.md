# USED Copy Commercial Data Change Report

## 1. Executive Summary

Phase B makes `DiscoQrCopy.precioVenta` and `DiscoQrCopy.condicionFisica` the honest commercial source for newly created `USADO` physical copies whenever the creation operation explicitly knows those values.

The implementation initializes only the exact copy rows returned by the current ordinary-create or Discogs Excel receipt operation. It does not copy a current parent value into retained history. Catalog summaries now use only `DISPONIBLE` USED copies, while the detailed copy panel continues to show available, sold, and removed identities with their own persisted data.

No historical copy values were inferred or backfilled. No migration was created. NUEVO behavior did not change. Stock, Dashboard valuation, Libro de Ventas, monthly financial summaries, profit, debts, pre-sales, and pricing configuration were not changed. Nothing was deployed.

## 2. Creation Paths Audited

| Path | Parent sale price known? | Parent physical condition known? | Previously wrote copy price/condition? | Safe initialization decision |
| --- | --- | --- | --- | --- |
| Manual Discogs receipt | Yes, from the submitted receipt | Yes, from the submitted receipt | Yes / Yes | Already safe because the service assigns both values to the exact created copies |
| Discogs Excel receipt | When explicitly present on that row | When explicitly present on that row | No / No | Initialize only `ReceiptResult.createdCopies()` from that row |
| Ordinary/manual Catalog creation | Explicit price is known when the saved mode is `MANUAL`; condition is known when submitted | Same | No / No | Initialize only the copies created by that create call |
| Quantity increase | Parent may have values, but the request carries only a count | Parent may have values, but the request carries only a count | No / No | Keep new copy fields null because the operation supplies no copy-specific data |
| Generic synchronization / Catalog edit | A current parent value may exist | A current parent value may exist | No / No | Do not infer; generic synchronization remains commercial-data-neutral |
| Discogs receipt into an existing release | Row-specific values may be present | Row-specific values may be present | No / No for Excel; Yes / Yes for manual Discogs | Use only exact created rows from the active receipt |
| VinylFuture/Pedido synchronization | An automatic/calculated parent sale price may exist | No proven copy-specific grade | No / No | Leave copy fields null; an automatic parent price is not treated as an explicit copy price |
| Direct copy helpers (`synchronize`, `addCopies`) | Context-dependent | Context-dependent | No / No | Remain neutral; callers with proved operation data must explicitly initialize returned created rows |

The audit also covered Catalog create/edit entry points, the common QR synchronization service, Discogs stock receipt matching, and the Pedido/VinylFuture path that can classify a product as USED.

## 3. Previous Behavior by Creation Path

- Manual Discogs already persisted submitted price and grade on each exact created copy and retained batch/source lineage.
- Discogs Excel persisted row commercial values on `Disco` but its generated `DiscoQrCopy` rows could remain null.
- Ordinary USED creation persisted submitted commercial data on the parent, while generic QR synchronization created copy rows without it.
- Quantity increases and generic synchronization created anonymous commercial fields. This was ambiguous but honest for a count-only operation.
- Catalog USED summaries considered retained non-removed rows, so sold copies could affect current ranges; retained totals could also make one available copy look like multi-copy available stock.
- The detailed copy view already used copy fields without silently replacing them with parent values.
- Nueva Venta already preferred an explicit copy price and retained its scoped parent fallback for null copy prices.

## 4. Source-of-Truth Rule Applied

For USED copy-specific price and grade, the authoritative fields are:

- `DiscoQrCopy.precioVenta`
- `DiscoQrCopy.condicionFisica`

`Disco.precioVenta` and `Disco.condicionFisica` remain aggregate/current product data; they are not a universal historical fallback. The new initializer requires:

1. an `USADO` product;
2. the exact list of rows created by the current operation; and
3. an explicit price and/or condition supplied by that operation.

It validates that every supplied copy belongs to the product. Empty or unknown values cause no write. NUEVO and all other categories are rejected by the category guard.

## 5. Manual Discogs Behavior

Manual Discogs was inspected and intentionally left unchanged. `DiscogsManualBatchService.assignCopiesToBatch` continues to assign the submitted price and physical condition to the exact created copies.

Batch provenance, source ownership, percentage behavior, operation lineage, duplicate handling, and exact-copy sale requirements were not redesigned. Existing regression coverage continues to prove the submitted values are retained.

## 6. Discogs Excel Behavior

After `DiscogsCatalogStockService.receive(...)`, the Excel import now passes `ReceiptResult.createdCopies()` plus that row's `manualPriceUyu` and normalized `manualCondition` to the guarded initializer.

This supports both a new release and another receipt of an existing release. Repeated rows for the same release can therefore produce independent copy values, for example copy 1 at UYU 700 / VG and copy 2 at UYU 900 / NM. A missing row value stays null and is not replaced from the product.

Product matching, Discogs identity, metadata enrichment, row status, and optional track behavior were not changed.

## 7. Ordinary USED Creation Behavior

`DiscoService.crearDisco(...)` now retains the synchronization result and initializes only the copies added by that creation call.

- An explicitly submitted manual sale price is stored on each copy created by the call.
- An explicitly submitted physical condition is stored on each copy created by the call.
- Automatic/calculated parent prices are not promoted to copy truth.
- Missing exact data remains null.
- The initializer is restricted to `USADO`, so the same shared service path cannot add per-copy commercial data to NUEVO.

## 8. Quantity Increase Behavior

The quantity endpoint still receives only the desired count. It does not receive a price or condition for the additional unit. Consequently, a USED increase from one to two copies creates a distinct QR copy whose `precioVenta` and `condicionFisica` remain null.

The original copy retains its values. No parent or sibling value is copied to the new copy. The quantity endpoint was not redesigned in this phase.

## 9. Legacy Data Handling

Existing copy rows were not modified. There is no historical backfill and no data migration.

An ambiguous old record can continue to display `Sin precio específico` and/or `Sin condición registrada`. Generic synchronization and edit flows do not fill those gaps from current parent fields.

## 10. Catalog AVAILABLE-Copy Summary

USED commercial summaries now derive price and condition only from copy rows whose state is exactly `DISPONIBLE`.

- Multiple available prices produce an available-stock range.
- A single available copy displays that copy's exact price and grade.
- Sold and removed values do not widen the current range or alter the current condition summary.
- One available copy plus any amount of retained history behaves commercially as one available copy.
- Before copy details load, the row uses `cantidadCopias` and an honest “ver detalle” label rather than displaying a parent value as copy truth.
- Zero available copies reports `Sin copias disponibles`.

## 11. Detailed USED Copy Presentation

The existing detailed USED presentation remains intact. It shows retained physical identities, including `DISPONIBLE`, `VENDIDO`, and `REMOVED`, with each selected copy's:

- exact persisted price;
- exact persisted physical condition;
- provenance;
- lifecycle state; and
- QR identity.

Null fields render as `Sin precio específico` and `Sin condición registrada`. Product price or condition is not silently substituted in this panel.

## 12. Nueva Venta Compatibility

Nueva Venta was inspected but not changed. When the operator selects an exact USED copy with `DiscoQrCopy.precioVenta`, that price remains the sale price used for the selected copy. Tests continue to keep independently priced LO/NM and SV3/VG+ copies separate.

The existing fallback from a null copy price to the parent product price remains in place, as required by this phase. Generic/Excel inventory can still use product-level arbitrary reservation where the current lifecycle policy permits it; exact-copy policy redesign is deferred.

## 13. NUEVO Regression Confirmation

Phase A behavior remains intact:

- one product sale price;
- available product quantity;
- compact QR-oriented physical-copy section;
- unique QR per retained physical identity; and
- no per-copy commercial price, condition, or provenance presentation.

Backend unit coverage also proves the USED initializer performs no write for a NUEVO product. There was no semantic change to NUEVO.

## 14. Files Modified

Production:

- `sonograma-backend/src/main/java/com/sonograma/service/DiscoQrCopyService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/DiscoService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/importacion/DiscogsImportJobService.java`
- `frontend/src/pages/DiscosCatalogo.jsx`

Tests:

- `sonograma-backend/src/test/java/com/sonograma/service/DiscoQrCopyServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/controller/DiscoQrCopyReadModelIntegrationTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/importacion/DiscogsImportJobServiceTest.java`
- `frontend/src/pages/DiscosCatalogo.test.jsx`

Documentation:

- `USED_COPY_COMMERCIAL_DATA_CHANGE_REPORT.md`

No migration, entity-schema change, Stock file, Libro de Ventas file, financial service, removal endpoint, or deployment configuration was added or modified for Phase B.

## 15. Tests Added/Updated

- Direct initializer tests cover exact USED rows and the NUEVO category guard.
- Ordinary USED integration tests cover explicit price/condition initialization, missing-data nulls, quantity increase nulls, retained original values, and distinct QR identity.
- Discogs Excel tests cover independent values across repeated receipts and null preservation when source values are absent.
- Existing manual Discogs tests continue to cover exact submitted copy price and condition.
- Catalog tests cover two available USED copies with independent detail and range summaries, AVAILABLE-only summary behavior, one available copy plus sold/removed history, honest null labels, no parent substitution, retained history visibility, and Phase A NUEVO behavior.
- Existing Nueva Venta tests cover explicit copy-price selection and the intentionally retained null-price fallback.

## 16. Test/Build Results

All verification passed:

- Backend focused tests: 86 tests passed, 0 failures, 0 errors. Command covered `DiscoQrCopyServiceTest`, `DiscoQrCopyReadModelIntegrationTest`, `DiscogsImportJobServiceTest`, `ManualDiscogsReceiptOperationServiceTest`, and `DiscogsCatalogStockServiceTest`.
- Backend package verification: `mvn -q package -DskipTests` passed.
- Catalog frontend: 39 tests passed.
- Nueva Venta frontend: 14 tests passed.
- ESLint passed for `DiscosCatalogo.jsx` and `DiscosCatalogo.test.jsx`.
- Frontend production build passed.
- `git diff --check` passed.

## 17. Remaining Data Gaps

- Legacy copies with no provable exact price or condition remain null by design.
- Quantity increases cannot capture per-copy commercial data until the API/UI accepts it explicitly.
- Automatic VinylFuture/Pedido pricing is a product calculation, not proved copy-specific input, so generated USED copy fields remain null.
- Catalog summaries require the copy-detail fetch before they can replace the honest pending label with exact copy values.
- Nueva Venta still has a parent-price fallback for a selected copy whose exact price is null.

## 18. Findings Deferred to Phase C/D

- Retained copy removal, decrement semantics, `/retiro`, terminal REMOVED behavior, and QR deletion behavior remain Phase C work.
- A broader exact-copy sale policy for generic and Excel USED inventory remains lifecycle work.
- Any operator workflow for editing an exact copy's missing price/condition is outside this phase.
- Historical reconciliation must use provenance-backed evidence; it must not become a blanket parent-to-copy migration.
- Financial reporting, Libro de Ventas, and copy-based valuation remain later-phase work.

## 19. Stock-Valuation Readiness

The model is more ready for future copy-based Stock valuation because new manual and Excel USED receipts can retain exact per-unit sale price and condition, and current Catalog summaries distinguish available commercial units from history.

It is not yet sufficient to switch valuation: legacy nulls remain, acquisition cost is still not universally copy-specific, count-only additions lack commercial metadata, and lifecycle/exact-selection policy is incomplete. Stock projected value and Dashboard valuation were intentionally not modified.

STATUS: PASS
