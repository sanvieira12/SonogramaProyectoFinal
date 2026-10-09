# PHYSICAL COPY LIFECYCLE HARDENING REPORT

## 1. Executive Summary

Phase D hardens Sonograma's physical-copy lifecycle without changing Stock valuation, Libro de Ventas formulas, Balance final, or deployment state.

For every product that already has `DiscoQrCopy` rows, the persisted count of rows in `DISPONIBLE` is now the operational stock authority. Supported mutations recalculate `Disco.cantidadCopias`, derive the parent state, and synchronize the compatibility parent QR alias from those rows. Generic NEW decrements retain excess available rows as `REMOVED` instead of deleting them. USED sales require an exact physical row: a sole ordinary non-manual USED copy may be selected automatically, while multiple copies and manual Discogs inventory require explicit selection.

Modern cancellation restores the exact IDs recorded by the sale. A legacy sale without `copyIdsSnapshot` now fails safely before cancellation or stock mutation whenever physical rows exist. Aggregate-only legacy restoration remains only for products with no physical-copy model.

Explicit confirmations:

- Physical rows are the operational quantity authority wherever physical rows exist: **yes**.
- Every USED sale with AVAILABLE physical rows reserves an exact copy: **yes**.
- Legacy cancellation can create aggregate-only stock for a modeled physical inventory product: **no**.
- Historically referenced copies can be destroyed through normal aggregate decrement: **no**; decrement retains rows as `REMOVED`.
- Copy QR identities were regenerated: **no**.
- A migration was created: **no**.
- Stock valuation was changed: **no**.
- Libro de Ventas formulas or approved refresh rules were changed: **no**.
- Deployment occurred: **no**.

## 2. Mutation Paths Audited

| Mutation | Aggregate quantity | Physical rows/state | Parent state | Parent QR alias | Historical reference behavior |
|---|---|---|---|---|---|
| Catalog quantity `+` | Recomputed from AVAILABLE rows | Creates AVAILABLE rows | Derived | Prefers AVAILABLE | Existing rows retained |
| Catalog quantity `-` for NEW | Recomputed from AVAILABLE rows | Excess AVAILABLE rows become REMOVED | Derived | Prefers remaining AVAILABLE | No row deletion |
| Catalog quantity `-` for USED/manual | Not changed | Rejected; exact-copy removal required | Unchanged | Unchanged | Prevents ambiguous identity loss |
| Product state change | Recomputed by authority service | Does not mass-change copies | Derived; unsafe zero-stock states rejected | Synchronized | Sold/removed history untouched |
| Exact copy status change | Recomputed | Locks and changes one row | Derived | Synchronized | Sale-linked SOLD cannot be generically revived; REMOVED remains terminal |
| Normal Nueva Venta | Recomputed | Reserves persisted row(s) | Derived | Synchronized | Reserved IDs stored in sale detail snapshot |
| QR Nueva Venta | Recomputed | Locks the matching exact QR row | Derived | Synchronized | QR identity remains exact |
| Sale edit/cancellation | Recomputed | Restores exact snapshot rows | Derived | Synchronized | Missing modeled identity fails before mutation |
| Debt deletion/restoration | Recomputed | Restores exact snapshot rows | Derived | Synchronized | Missing/foreign/reused identity rejected |
| Retained removal | Recomputed | Exact AVAILABLE row becomes REMOVED | Derived | Synchronized | Row, QR, provenance, and metadata retained |
| Administrative hard delete | Recomputed after allowed deletion | Exact row only | Derived | Synchronized | Existing sale/reservation/pre-sale/manual-lineage guards preserved |
| Manual Discogs receipt | Derived from rows | Creates exact batch-linked rows | Derived | Synchronized | Manual lineage retained |
| Discogs Excel receipt/retry | Derived from rows | Creates/synchronizes exact rows | Derived | Synchronized | Existing rows and Phase B commercial data retained |
| VinylFuture receipt | Derived from rows | Adds AVAILABLE rows | Derived | Synchronized | Existing sold rows unaffected |
| Legacy restoration without rows | Compatibility aggregate path | No physical model exists | Existing compatibility behavior | Legacy alias retained | Documented exception only |

Services that directly mutate `Disco.cantidadCopias` were inspected in the catalog, sale, debt, Discogs, and VinylFuture paths. Changes were limited to the lifecycle inconsistencies in scope.

## 3. Inventory Authority / Invariant

The enforced invariant for a modeled product is:

```text
Disco.cantidadCopias = COUNT(DiscoQrCopy WHERE estado = DISPONIBLE)
```

`DiscoEstadoService.aplicar` now sets the aggregate from the physical count, synchronizes the parent QR alias, and applies the existing state derivation. Sale availability checks use AVAILABLE physical rows and no longer reject a valid physical copy merely because a stale parent state says `SIN_STOCK`.

Legacy products with no physical rows keep only the narrowly required restoration compatibility described in section 9; no migration or speculative backfill was added.

## 4. NEW Quantity Decrement

NEW retains its simple product-level quantity controls and does not expose USED commercial-copy UX. A reduction such as 3 -> 2 selects only AVAILABLE rows outside the retained target set, marks one `REMOVED`, and then derives aggregate/state/alias from the persisted rows.

The 1 -> 0 case produces zero AVAILABLE rows, aggregate zero, and the existing derived zero-stock state. SOLD and already REMOVED rows are not decrement candidates.

## 5. Generic Aggregate Decrement Policy

`synchronizeAvailableCopiesWithResult` no longer calls `deleteAll` for excess AVAILABLE rows. Phase D adopts one consistent safe policy: all generic decrement candidates are retained as `REMOVED` with:

- reason `REMOVED_FROM_INVENTORY`;
- note `Ajuste de cantidad disponible`;
- actor `system:aggregate-quantity`;
- a disposal timestamp;
- the original row ID and QR unchanged.

This intentionally favors durable identity over a split referenced/unreferenced deletion policy. It is simple, auditable, and prevents the generic path from silently destroying history.

The tested AVAILABLE -> SOLD -> cancellation -> AVAILABLE -> aggregate decrement sequence retains the same row as REMOVED and preserves the sale's `copyIdsSnapshot`.

## 6. USED Exact-Copy Sale Policy

Sale search now sets `requiresExactCopySelection` for the product category `USADO`, independently of manual Discogs batch presence. This covers ordinary/manual USED, Discogs Excel USED, manual Discogs USED, and legacy USED products that have physical rows.

Backend enforcement is independent of the UI:

- one ordinary, non-manual AVAILABLE USED row may be auto-selected, but that precise row is locked and sold;
- multiple AVAILABLE USED rows require a submitted copy ID/QR;
- manual Discogs USED continues to require explicit exact selection even when only one row exists;
- product-level arbitrary fallback remains available only for equivalent NEW inventory.

The existing Nueva Venta selector consumes the broadened backend flag, auto-selects one available copy, and requires a choice among multiple copies. No new frontend state model was needed.

## 7. QR Sale Behavior

QR remains an exact physical-copy lookup. An AVAILABLE QR locks and sells that exact row. A SOLD or REMOVED QR is rejected. The QR path never substitutes another row from the same product, and no physical QR is regenerated.

Phase D also preserves the selected USED copy's explicit price and physical condition/provenance presentation. Existing legacy null-price fallback remains unchanged; no missing price or condition is invented or backfilled.

## 8. Modern Sale Cancellation

Modern sale details store the reserved physical IDs in `copyIdsSnapshot`. Cancellation restores those exact locked rows from SOLD to AVAILABLE, validates that every ID exists, validates uniqueness and ownership by the sold product, and rejects rows that no longer have the expected SOLD state.

The restored row keeps its original database ID and QR. No replacement row is created. Aggregate quantity, product state, and parent alias are then derived from physical inventory.

## 9. Legacy Cancellation Policy

When a sale lacks `copyIdsSnapshot` and the product has physical-copy rows, cancellation/edit restoration now throws a clear business conflict before any stock restoration or sale-state change. The transaction therefore cannot partially cancel financially while leaving inventory inconsistent.

Legacy cancellation cannot increment aggregate-only stock for a modeled product. The aggregate restoration path remains only when the product has no physical-copy rows at all, because exact identity does not exist in that legacy model and current architecture still requires compatibility.

## 10. Debt / Other Restoration Paths

Debt deletion already routes modeled products through strict exact-copy restoration. Phase D verified and retained these guards:

- a snapshot is mandatory when physical rows exist;
- every copy must belong to the product;
- every copy must still be SOLD;
- a copy reused by another active sale is rejected;
- aggregate/state are derived after exact restoration;
- products with no physical model retain the legacy aggregate path.

No debt amounts, payment calculations, or other financial formulas changed.

## 11. Product State Mutation Safety

A product-level request for `VENDIDO` or `SIN_STOCK` is rejected while AVAILABLE physical rows remain. The endpoint no longer mass-marks available copies SOLD or drives a quantity decrement through a state change.

After an accepted state request, the authority service derives the effective state from physical rows. Existing `RESERVADO` behavior is preserved while AVAILABLE rows remain. A generic copy-status operation cannot revive a historically sale-linked SOLD copy; restoration must pass through exact sale cancellation. REMOVED rows cannot be revived or sold.

## 12. Parent QR Alias Synchronization

`Disco.codigoQr` remains a compatibility alias, not physical identity authority. After supported lifecycle mutations it now:

1. prefers the first AVAILABLE physical copy;
2. otherwise falls back to the first retained physical row for legacy compatibility;
3. preserves the legacy parent alias when no physical rows exist.

Sale, cancellation, removal, decrement, receipt/import, and state derivation all reach this synchronization through `DiscoEstadoService.aplicar`. Copy QR values are never replaced or regenerated.

## 13. Hard-Delete Safety

The administrative hard-delete endpoint remains available. Existing centralized commercial-reference checks continue to block destructive operations when a copy is referenced by a sale snapshot, has ambiguous sold history, participates in active reservation/pre-sale state, or carries protected manual Discogs lineage.

Normal aggregate decrement no longer invokes repository deletion at all. Retained REMOVED rows therefore cannot be destroyed accidentally through quantity controls, and REMOVED terminality remains enforced.

## 14. Import Synchronization

Discogs matching, metadata rules, manual-batch semantics, Excel receipt semantics, and VinylFuture identity semantics were not redesigned.

- Discogs catalog receipt already creates/synchronizes physical rows and derives state.
- Existing-product Discogs Excel metadata/retry paths now first take the AVAILABLE physical count as aggregate authority, synchronize, derive state/alias, and save.
- VinylFuture receipt now derives parent quantity/state/alias after physical-row synchronization.
- Manual Discogs receipt restrictions and Phase B initialization of only newly created USED rows remain intact.

Receipt tests verify aggregate count equals AVAILABLE physical count and that sold rows are not altered by later receipts.

## 15. Frontend Refresh Behavior

Phase C's authoritative refresh and stale-response protection remain unchanged. Phase D did not introduce speculative client-side inventory or a parallel copy model.

The existing Nueva Venta exact-copy selector required no production component rewrite: the backend now correctly marks all USED products with physical availability as exact-selection inventory. The frontend test was expanded to prove automatic exact selection, condition display, provenance fallback, and copy price for an ordinary USED product.

The React performance review guidance was applied by keeping this change on the existing data/selection path and avoiding new effects, mirrored state, or component churn.

## 16. Phase A Regression

Phase A NEW catalog presentation remains intact. NEW sale search remains product-level, NEW quantity controls stay simple, and no copy-specific commercial presentation was added for NEW. `DiscosCatalogo` regression tests pass.

## 17. Phase B Regression

Phase B USED commercial data remains authoritative at copy level. Exact selection continues to use the selected copy's explicit price, physical condition, and provenance; legacy nulls keep the documented fallback. No backfill or migration was added. Nueva Venta and Discogs import regression tests pass.

## 18. Phase C Regression

Phase C retained-removal behavior remains intact. REMOVED is terminal, removal requires the explicit retained-removal flow and reason, stale catalog response protection remains, and historically protected/manual-lineage rows remain guarded. Permanent-deletion/removal regression tests pass.

## 19. Files Modified

Phase D production changes:

- `sonograma-backend/src/main/java/com/sonograma/service/DiscoQrCopyService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/DiscoEstadoService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/DiscoService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VentaService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VinylFutureCatalogStockService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/importacion/DiscogsImportJobService.java`

Phase D test changes:

- `sonograma-backend/src/test/java/com/sonograma/controller/DiscoSaleSearchIntegrationTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscoEstadoServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscoPermanentDeletionIntegrationTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscoQrCopyServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscogsCatalogStockServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/ExactCopySaleIntegrationTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/VentaServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/VinylFutureCatalogStockServiceTest.java`
- `frontend/src/pages/NuevaVenta.test.jsx`

`DiscogsImportJobServiceTest.java` contains import regression coverage used by this verification; its existing workspace diff also includes prior Phase B work. Other dirty workspace files predated Phase D and were not folded into this implementation.

## 20. Tests Added/Updated

Coverage added or updated for:

- NEW 3 -> 2 and 1 -> 0 safe retained decrement;
- exact aggregate count, derived state, disposition metadata, and parent alias;
- previously sold/cancelled NEW copy retained by later decrement with its sale snapshot intact;
- ordinary USED sole-copy exact auto-reservation;
- ordinary USED multiple-copy explicit-selection conflict;
- manual Discogs exact-selection preservation;
- NEW product-level fallback preservation;
- ordinary and Discogs Excel-style USED sale-search exact-selection flags;
- ordinary USED frontend auto-selection with copy price and condition;
- AVAILABLE QR exact sale and SOLD/REMOVED rejection through the QR/exact-copy suites;
- modern exact cancellation and copy ownership validation;
- safe legacy cancellation failure before sale or aggregate mutation;
- strict debt restoration without aggregate-only fallback for modeled products;
- REMOVED terminality;
- historically referenced hard-delete protection;
- product-state rejection while AVAILABLE rows exist;
- prevention of generic historical SOLD -> AVAILABLE restoration;
- parent QR alias after sale/removal/decrement;
- Discogs and VinylFuture receipt synchronization.

## 21. Test/Build Results

All executed verification passed:

- Backend lifecycle/import/debt/QR/deletion regression suite: **207 tests passed**, 0 failures, 0 errors, 0 skipped.
- Frontend focused suite (`DiscosCatalogo`, `NuevaVenta`, API): **83 tests passed** across 3 files.
- ESLint on the Phase D modified frontend test: **passed** with no findings.
- Frontend production build: **passed** (`vite build`).
- Backend package verification: **passed** (`mvn -q package -DskipTests`).
- Patch hygiene: **passed** (`git diff --check`).

No deployment command was run.

## 22. Remaining Legacy/Data Risks

- No data migration or production reconciliation was performed. Pre-existing aggregate/physical drift, if present in stored production data, still requires an explicit audit/reconciliation operation before relying on historical aggregates.
- `copyIdsSnapshot` is historical text rather than a database foreign-key relationship; service validation protects supported mutations, but the database does not independently enforce that link.
- Products with no physical rows retain the documented legacy aggregate-only restoration compatibility.
- The administrative hard-delete path remains intentionally available for eligible unreferenced correction data; its business use should remain restricted.
- A sold/imported copy with no Sonograma sale record may not provide the same historical proof as a sale snapshot; that legacy/imported-data policy should be reviewed during data reconciliation rather than guessed in this phase.
- Reservation and pre-sale models identify the parent product rather than a physical copy. Destructive operations therefore continue to use conservative product-level guards.
- Phase D did not introduce a new database constraint or migration for cross-row aggregate equality; consistency is enforced at the audited transactional service boundaries.

## 23. Readiness for Phase E Stock Valuation

The physical-copy lifecycle is ready to serve as the inventory foundation for Phase E: modeled inventory has one operational quantity source, USED sales resolve exact rows, cancellation/restoration preserves exact identity, generic NEW decrement retains history, parent state/alias are synchronized, and import receipt paths converge on the same authority.

Phase E should consume the physical inventory model without reopening lifecycle mutation semantics. Before production rollout of valuation changes, separately reconcile any legacy data drift identified in section 22. No Stock valuation code was changed in Phase D.

STATUS: PASS
