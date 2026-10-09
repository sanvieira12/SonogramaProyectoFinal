# USED Copy Safe Removal Change Report

## 1. Executive Summary

Phase C is complete. The Catalog now lets an administrator retire one exact AVAILABLE USADO physical copy through the existing retained-removal lifecycle:

`POST /discos/{idDisco}/copias/{idCopia}/retiro`

The normal USADO operation changes the selected row to `REMOVED`; it does not delete that row. The backend remains authoritative for the resulting copy state, available quantity, and parent product state. The Catalog then reconciles the returned product and force-refreshes the physical-copy detail.

No physical copy is hard-deleted by the new normal USED workflow. The removed copy retains its database identity, copy number, QR identity, price, condition, provenance, and removal history.

## 2. Existing Retained-Removal Contract

The existing controller, DTO, service, and persistence path were inspected and reused rather than duplicated.

- Endpoint: `POST /discos/{idDisco}/copias/{idCopia}/retiro`.
- Authorization: administrator only.
- Request body: `{ "reason": "...", "note": "..." }`.
- `reason` is required and uses the backend enum.
- Supported reasons: `DATA_ENTRY_CORRECTION`, `RETURNED_TO_PROVIDER`, `DAMAGED`, `REMOVED_FROM_INVENTORY`, and `OTHER`.
- `note` is optional and has a 2,000-character maximum.
- Eligible operational source state: `DISPONIBLE`. Sold and already removed copies are rejected.
- The service locks and verifies the exact product/copy relationship, records reason, note, timestamp, and actor, and persists the same copy row as `REMOVED`.
- Existing sale-history, active-reservation, pending-pre-sale, ownership, and lifecycle guards remain in force.
- Manual Discogs copies use this retained lifecycle; their hard-delete restrictions remain intact.
- Available quantity and parent state are recalculated by the existing backend service from physical rows.
- Copy-detail responses already expose the retained removal metadata used by Catalog.

No second endpoint, copy state, DTO, or removal mechanism was introduced.

## 3. Previous Catalog Behavior

Catalog had no business action for retained removal of an exact USED copy. The QR-management modal exposed a generic hard-delete action, and the product-row quantity decrement could reach aggregate synchronization/deletion behavior. That presentation made destructive deletion too easy to confuse with normal USED inventory removal.

Copy-detail requests also had no protection against an older in-flight response overwriting a newer authoritative result.

## 4. New USED Removal UX

An exact USADO copy detail now shows `Retirar Copia N` only when the selected copy is `DISPONIBLE`. The confirmation identifies:

- the product/release;
- the exact copy number;
- the current copy state;
- that available stock will decrease while QR and history remain retained.

The action is absent for `VENDIDO` and `REMOVED` copies. It is also absent from NUEVO's compact copy presentation. The USADO row-level quantity-minus control is disabled and directs the operator to select and retire the exact physical copy instead.

## 5. Reason / Note Handling

The confirmation uses only the five backend-supported reason values. A reason must be selected before confirmation is enabled. The note is optional and enforces the backend's 2,000-character limit.

On success, the UI displays retained metadata where available: reason, note, timestamp, and actor. No frontend-only reason values or synthetic metadata were added.

## 6. Hard-Delete UI Policy

For USADO, retained removal is the normal operational action. The physical-copy QR modal no longer offers hard delete for a USED product and explains that the exact copy must be retired from its detail. A removed USED copy also has no generic status action that could suggest reactivation.

The backend hard-delete endpoint was not removed. The existing non-USED administrator data-correction path remains available, and product-level permanent deletion remains separately named and unchanged. NUEVO quantity decrement was not redesigned in this phase.

## 7. REMOVED Terminality

The backend generic status mutation now rejects every attempt to mutate a copy whose current state is `REMOVED`. This closes both audited revival paths:

- `REMOVED -> DISPONIBLE` is rejected.
- `REMOVED -> VENDIDO` is rejected.

The existing rule that creation of `REMOVED` requires the explicit retained-removal endpoint and reason also remains intact. No restore path was added.

## 8. QR Preservation

Retained removal updates the existing physical-copy row; it does not regenerate, null, or delete its QR. Integration coverage confirms that the removed QR remains identifiable and viewable. Existing exact-sale validation rejects the same copy as saleable inventory once its state is `REMOVED`.

Removed QR identity is retained.

## 9. Quantity / Parent-State Synchronization

The frontend does not derive a replacement quantity or parent state. The removal service recalculates from persisted physical rows, returns the authoritative product, and Catalog reconciles that response.

Verified backend outcomes:

- two available copies, one removed: quantity `2 -> 1`, parent remains `DISPONIBLE`;
- three available copies, one removed: quantity `3 -> 2`, parent remains `DISPONIBLE`;
- final available copy removed with no sold sibling: quantity `1 -> 0`, parent becomes `SIN_STOCK`;
- final available copy removed while a sold sibling remains: quantity reaches `0`, sold history is untouched, and the existing derivation produces parent `VENDIDO`.

No frontend parent-state rule was hardcoded.

## 10. Catalog Refresh Strategy

After a successful call, Catalog:

1. reconciles the backend-returned product across the catalog list and active selected/hovered/slide-over/QR views;
2. force-refetches the affected product's complete physical-copy detail;
3. closes the confirmation only after the operation and authoritative refresh succeed.

A per-product request generation guard prevents an older copy-detail response from overwriting a newer removal refresh. This is deliberately scoped to `copyDetailsByProduct`; Catalog networking was not broadly refactored.

## 11. 2 -> 1 -> 0 Results

The required lifecycle sequence is covered at backend and UI levels.

- At `2 -> 1`, only the selected exact copy becomes `REMOVED`; its sibling remains `DISPONIBLE`, and the parent remains available.
- At `1 -> 0`, the selected row remains stored as `REMOVED`, quantity becomes zero, and Catalog respects the backend-derived parent state.
- At `3 -> 2`, exactly one available row is removed from the available count.
- The exact copy number and QR remain unchanged in every retained-removal case.

For the Phase B example, removing the UYU 900 / NM available copy from a UYU 700 / VG plus UYU 900 / NM pair changes the AVAILABLE summary from a range to UYU 700 / VG. The removed commercial history stays visible on the retained copy.

## 12. Commercial Conflict Handling

The UI performs no optimistic quantity decrement, copy deletion, or state mutation. If the backend rejects the operation because of sale history, reservation, pre-sale, ownership, or another lifecycle constraint, the dialog stays open, the existing product/copy state remains visible, and the backend message is shown in the existing error style.

The backend guards were neither bypassed nor weakened.

## 13. NUEVO Regression Confirmation

Phase A remains intact:

- NUEVO continues to show one product price and available quantity.
- NUEVO keeps compact QR identities rather than USED-style per-copy commercial panels.
- Retained-removal commercial UI was not added to NUEVO.
- NUEVO quantity decrement behavior was not redesigned.

The existing NUEVO Catalog regression coverage passes.

## 14. Phase B Regression Confirmation

Phase B remains intact:

- exact USED copy price and condition remain authoritative;
- AVAILABLE-only commercial summaries remain in effect;
- Discogs Excel initialization and manual Discogs behavior are unchanged;
- legacy-null policy is unchanged;
- removed copies cease to affect the current AVAILABLE summary but retain their historical commercial and provenance data.

The focused Catalog and backend read-model/sale regressions pass.

## 15. Files Modified

Phase C production changes:

- `frontend/src/api/sonograma.js`
- `frontend/src/pages/DiscosCatalogo.jsx`
- `sonograma-backend/src/main/java/com/sonograma/service/DiscoQrCopyService.java`

Phase C test changes:

- `frontend/src/api/sonograma.test.js`
- `frontend/src/pages/DiscosCatalogo.test.jsx`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscoQrCopyServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/DiscoPermanentDeletionIntegrationTest.java`

Documentation:

- `USED_COPY_SAFE_REMOVAL_CHANGE_REPORT.md`

The working tree also contains pre-existing Phase A, Phase B, and unrelated user changes; those are outside this Phase C file list and were preserved.

## 16. Tests Added/Updated

Coverage added or updated for:

- exact API method, route, and request body;
- exact USED confirmation identity, required reason, and optional note;
- successful retained removal and authoritative detail refresh;
- AVAILABLE-only summary recalculation after removal;
- retained QR, commercial data, removal metadata, and actor display;
- no action for SOLD or already REMOVED copies;
- conflict behavior without optimistic mutation;
- USED hard-delete suppression and NUEVO/non-USED administrative separation;
- stale copy-detail response rejection;
- `REMOVED` terminality for both generic revival targets;
- `2 -> 1`, `3 -> 2`, `1 -> 0`, and AVAILABLE-plus-SOLD backend derivation;
- retained endpoint administrator authorization;
- existing QR viewability, exact-sale rejection, sale/reservation/pre-sale guards, manual Discogs provenance, and hard-delete restrictions through the broader regression suite.

## 17. Test/Build Results

All requested verification passed on 2026-10-07:

- Focused Catalog tests: **43 passed**.
- Frontend API tests: **25 passed**.
- Nueva Venta regression tests: **14 passed**.
- ESLint on the modified Catalog/API source and test files: **PASS**.
- Frontend production build: **PASS** (`863` modules transformed).
- Focused backend copy/removal tests: **49 passed**.
- Broader backend copy lifecycle, retained-removal, read-model, sale, QR, manual Discogs, and import regression set: **125 passed**, 0 failures, 0 errors, 0 skipped.
- Backend package verification (`mvn -q package -DskipTests`): **PASS**.
- `git diff --check`: **PASS**.

The focused and broader backend counts overlap and are reported separately rather than summed.

## 18. Remaining Lifecycle Risks

- Generic hard-delete APIs still exist for explicit administrative data correction. Phase C reduces their visibility for normal USED operations but does not redesign or remove them.
- NUEVO's generic quantity-decrement/hard-delete concern remains as previously audited.
- Parent-level QR aliases can still differ from exact physical-copy identity in legacy data; the exact copy QR remains the lifecycle authority.
- Other generic aggregate/product-state operations remain available and should be reviewed as part of the next lifecycle-hardening phase rather than expanded here.
- Legacy records with incomplete commercial metadata continue under the Phase B null policy.

## 19. Deferred Phase D Work

Phase D should address the intentionally deferred cross-cutting lifecycle rules, including exact-copy selection policy for all relevant sale paths, remaining aggregate quantity/status mutation paths, NUEVO decrement semantics, legacy cancellation/restoration policy, imported-sold behavior, and any explicit restore operation. A restore, if ever required, must be a separate auditable business operation and not a generic status mutation.

Explicit scope confirmations:

- No physical copy was hard-deleted by the new normal USED workflow.
- Removed QR identity is retained.
- No Stock formula changed.
- No Libro de Ventas logic changed.
- No monthly financial summary, Balance final, profit, debt, pre-sale, conversion, or pricing-configuration logic changed for Phase C.
- No migration was created.
- No deployment occurred.

STATUS: PASS
