# NEW Catalog Presentation Change Report

## 1. Previous behavior

The Catalog selected its commercial presentation from the retained physical-copy count alone. Any product with more than one retained `DiscoQrCopy` entered the same per-copy UI, regardless of whether its normalized category was `NUEVO` or `USADO`.

As a result, multi-copy NEW products could display `Precios por copia`, copy-price ranges, `Condición por copia`, copy-specific commercial cards, and USED-oriented provenance even though their copies are commercially equivalent.

## 2. Root cause

`copyPriceSummary`, `copyConditionSummary`, and both Catalog detail surfaces were category-blind. They used `totalCopias`/retained copy details to choose a presentation and rendered `PhysicalCopiesSection` unconditionally.

The backend model and DTOs already provide everything required for the requested distinction:

- `condicion` identifies the normalized product category.
- `precioVenta` (through the existing Catalog price authority) provides the NEW product sale price.
- `cantidadCopias` provides the available physical-copy quantity.
- retained copy rows preserve their individual IDs, QR values, copy numbers, and states.

## 3. New NUEVO presentation

The frontend now explicitly branches on the normalized category `NUEVO`.

For NEW products:

- the Catalog row always shows the existing product/catalog sale price;
- the condition column identifies the product as `NUEVO` instead of deriving a copy-condition summary;
- detail and preview surfaces label the shared amount as `Precio venta`;
- available quantity continues to come from `cantidadCopias`;
- copy-specific price, physical condition, provenance, and USED-specific commercial cards are omitted;
- `Precios por copia`, copy-price ranges, `Condición por copia`, `Sin precio específico`, and `Sin condición registrada` are not produced for NEW products.

## 4. QR preservation

Every retained NEW physical copy remains a separate row with its existing identity. The new compact `Copias físicas / QR` section lists each copy number, lifecycle state, QR value, and an exact `Ver QR de Copia N` action.

That action reuses the existing QR modal and exact-copy selection path. No QR was regenerated, replaced with a parent QR, merged, or deleted.

## 5. Available vs retained copy handling

The NEW commercial quantity uses `disco.cantidadCopias`, the Catalog DTO's available-copy authority.

The compact QR section separately reports the number of retained QR entries loaded from copy details. Sold and removed entries remain visible and accessible, but are explicitly labeled `Vendida` and `Retirada`; they are not counted or described as available inventory.

## 6. Files modified

- `frontend/src/pages/DiscosCatalogo.jsx`
- `frontend/src/pages/DiscosCatalogo.test.jsx`
- `NEW_CATALOG_PRESENTATION_CHANGE_REPORT.md`

No backend, database, migration, Stock, pricing, sales, scanner, debt, pre-sale, VinylFuture, or import implementation file was changed by this work.

## 7. Tests added/updated

Focused Catalog coverage now verifies:

- one-copy NEW product price and exact QR access, including the mobile slide-over path;
- three available NEW copies with one product price and three distinct QR identities;
- exact selection/view of a chosen NEW QR;
- two available copies plus retained sold and removed copies;
- sold and removed states and QR access without per-copy commercial fallbacks;
- an equivalent multi-copy USED product retaining its price range and detailed commercial copy panel;
- pre-existing tests that exercise USED/manual per-copy semantics now declare `condicion: 'USADO'` explicitly instead of inheriting a NEW fixture default.

## 8. Test/build results

All requested verification passed:

- `npm test -- src/pages/DiscosCatalogo.test.jsx`: **38 tests passed**.
- `npx eslint src/pages/DiscosCatalogo.jsx src/pages/DiscosCatalogo.test.jsx`: **passed with no findings**.
- `npm run build`: **production build passed** (`vite build`, 863 modules transformed).
- `git diff --check`: **passed**.

No deployment was performed.

## 9. USED regression confirmation

The existing `PhysicalCopiesSection` remains the non-NEW path and its price, condition, provenance, retained-removal detail, selection, and exact-QR behavior were not rewritten.

The focused USED regression confirms that two USED copies with different prices still produce the existing `UYU $925–$1.100` range and retain the `Procedencia`, `Precio`, and `Condición` fields. The broader Catalog file also passed all existing tests.

## 10. Financial/Stock regression confirmation

This implementation does not change pricing calculations, acquisition costs, EUR/UYU handling, `PricingSettingsPage`, Dashboard valuation, projected Stock value, or any Stock formula. NEW presentation reads the same existing product/catalog price authority and `cantidadCopias`; it adds no financial mutation.

## 11. Remaining observations

- The known generic quantity decrement can hard-delete available physical-copy rows. That mutation remains unchanged and should be handled in its dedicated lifecycle phase.
- The existing QR management modal retains its current state and deletion controls. Their behavior was intentionally left unchanged.
- No backend DTO change was necessary.
- This implementation changed **no backend inventory logic and no QR lifecycle logic**. It is frontend presentation and focused test coverage only.

STATUS: PASS
