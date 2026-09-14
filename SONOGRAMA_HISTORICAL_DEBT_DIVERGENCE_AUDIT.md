# Sonograma — Historical Debt / Sale Divergence Audit

Audit date: 2026-09-14 (America/Montevideo)

Scope: production records `deuda` 30, 53, 67, 89, and 102, linked respectively to `venta` 25, 34, 48, 76, and 90.

Production access used only the previously recovered read-only workflow:

```bash
ssh -i ~/.ssh/LightsailDefaultKey-us-east-1.pem ubuntu@tiendasonograma.com
```

Production database reads were executed through `sonograma-postgres` with `SELECT` statements only. The deployed application checkout reported commit `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5`. Local Git history and source were inspected read-only. No production file, database row, container, service, or migration was changed.

## 1. Safety Confirmation

- No `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP`, `TRUNCATE`, migration, deploy, repair, or baseline operation was executed.
- No Docker container or service was restarted.
- No backup was created or deleted.
- No SQL transaction was opened for a write; no correction SQL was executed.
- No passwords, tokens, private keys, database credentials, or unnecessary customer PII were printed.
- Only customer IDs were retained where needed to verify relationships.

## 2. Executive Summary

All five debts are linked to an existing sale through a single `deuda.id_venta` reference, and each linked sale has exactly one current debt row. The five sale detail sets reconcile to the stored sale totals:

| Debt / sale | Sale total | Detail-line sum | Debt total | Difference: debt minus sale | Sale state |
|---:|---:|---:|---:|---:|---|
| 30 / 25 | 1,900.00 | 1,900.00 | 2,300.00 | +400.00 | COMPLETADA |
| 53 / 34 | 1,390.00 | 1,390.00 | 790.00 | -600.00 | COMPLETADA |
| 67 / 48 | 17,573.00 | 17,573.00 | 8,640.00 | -8,933.00 | COMPLETADA |
| 89 / 76 | 1,350.00 | 1,350.00 | 1,000.00 | -350.00 | CANCELADA |
| 102 / 90 | 1,290.00 | 1,290.00 | 1,690.00 | +400.00 | COMPLETADA |

There are no `pago_deuda` rows for any of the five debts. Consequently, the expected paid amount is the stored initial paid amount in every case, and the expected pending amount is `max(monto_total - monto_pagado_inicial, 0)`.

For completeness, the remaining nullable sale fields relevant to provenance were also checked: all five have `id_disco=NULL`, `numero_recibo=NULL`, `observaciones=NULL`, `origen=NULL`, and `id_pre_venta_origen=NULL`; all five have `porcentaje_impuesto=0.00`. Customer-name snapshots were intentionally omitted as unnecessary PII.

The common pattern is not a single current sale-calculation error. Historical Git shows that sale creation calculated the sale and debt from the same total, while the historical debt edit endpoint accepted an independently entered `montoTotal` and did not update `venta.total` or `venta.total_final`. That makes later independent debt editing or a legacy “debt as pending amount” workflow possible, but no row-level audit trail proves which occurred.

Required correction classifications:

| Pair | Classification |
|---|---|
| Debt 30 / Sale 25 | **INSUFFICIENT EVIDENCE — LEAVE UNCHANGED** |
| Debt 53 / Sale 34 | **INSUFFICIENT EVIDENCE — LEAVE UNCHANGED** |
| Debt 67 / Sale 48 | **INSUFFICIENT EVIDENCE — LEAVE UNCHANGED** |
| Debt 89 / Sale 76 | **LEGITIMATE HISTORICAL DIFFERENCE** |
| Debt 102 / Sale 90 | **INSUFFICIENT EVIDENCE — LEAVE UNCHANGED** |

No pair meets the evidence threshold for a production correction. Therefore this report contains no executable or proposed correction SQL.

## 3. Pair 30 / 25

### Sale 25 — stored financial snapshot

| Field | Stored value |
|---|---|
| `id_venta` / `id_cliente` | 25 / 43 |
| `fecha_venta` | `2026-07-06 21:43:53.954105` |
| `canal_venta` / `tipo_entrega` | `LOCAL` / `RETIRO` |
| `estado` / `estado_pago` | `COMPLETADA` / `PARCIAL` |
| `numero_factura` | `F-2026-025` |
| `medio_pago` | `TRANSFERENCIA` |
| `subtotal` / `descuento_porcentaje` | 1,900.00 / 0.00 |
| `costo_disco` / `costo_envio` / `monto_impuesto` / `otros_costos` | 0.00 / 0.00 / 0.00 / 0.00 |
| `precio_venta` / `total` / `total_final` | 1,900.00 / 1,900.00 / 1,900.00 |
| `ganancia_estimada` | 1,900.00 |
| `monto_pagado` / `monto_deuda` | 1,500.00 / 400.00 |
| `origen` / `id_pre_venta_origen` | `NULL` / `NULL` |

### Sale-detail reconciliation

| Detail | Product/copy identity | Manual | Qty | Unit price | Line value | Acquisition snapshot |
|---:|---|---|---:|---:|---:|---|
| 93 | `id_disco=NULL`; description snapshot `ninja tools vol 4`; no copy snapshot | true | 1 | 1,900.00 | 1,900.00 | all acquisition fields `NULL` |

Detail count is 1 and total quantity is 1. The line sum 1,900.00 equals `subtotal`, `total`, and `total_final`. The stored discount is 0.00 and there is no separate stored 400.00 discount. Sale-side reconciliation: **RECONCILED**.

### Debt and payment reconciliation

| Field | Stored value |
|---|---|
| `id_deuda` / `id_venta` / `id_cliente` | 30 / 25 / 43 |
| `numero_factura` | `F-2026-025` |
| `monto_total` | 2,300.00 |
| `monto_pagado_inicial` / `monto_pagado` / `monto_pendiente` | 1,500.00 / 1,500.00 / 800.00 |
| `fecha_venta` / `fecha_deuda` / `fecha_ultimo_pago` | 2026-07-06 / 2026-07-06 / `NULL` |
| `fecha_creacion` / `updated_at` | 2026-07-06 21:43:53.979039 / 2026-07-17 02:29:51.934785 |
| `estado_pago` / `activa` | `PARCIAL` / `false` |
| manual debtor fields / description / notes | all `NULL` |

Payment query result: zero rows. Expected paid = 1,500.00 + 0.00 = 1,500.00; expected pending = 800.00. The debt’s internal movement balance is **RECONCILED**. Its linked-sale cache is not: debt pending 800.00 versus sale debt 400.00, while paid and payment status agree.

### Relationship and historical evidence

- The debt was created approximately 25 milliseconds after the sale, consistent with the same sale transaction.
- The sale and debt have the same customer ID and invoice number. There is exactly one debt for sale 25 and exactly one sale row for ID 25.
- There is no pre-sale origin (`venta.origen` and `id_pre_venta_origen` are null).
- Customer 43 has another separate fully paid sale (sale 37) and no other linked debt for sale 25; this does not establish a correction value.
- `manual_item=true` is **PROVEN** for the sale line. A product/catalog or selected-copy explanation is not supported.
- A stored sale discount of 400.00 is **NOT SUPPORTED**.
- Same-total sale/debt creation is **SUPPORTED** by the historical code before 2026-07-06, so it does not explain the divergence at creation.
- Independent debt editing is **POSSIBLE**: the historical `PUT /deudas/{idDeuda}` path accepted `montoTotal` without synchronizing sale total fields. Debt 30’s later `updated_at` supports a later debt write but does not prove the changed field or actor.
- Sale-total editing is **POSSIBLE** in historical code, but no sale audit timestamp or request evidence proves it occurred.
- A payment-driven difference is **NOT SUPPORTED** because there are no payment rows and no later payment date.
- A pre-sale conversion, selected-item financing, cancellation-unsync, or discount-change explanation is **NOT SUPPORTED** for this row.

### Classification and correction decision

**INSUFFICIENT EVIDENCE — LEAVE UNCHANGED.** The sale side is structurally strong: manual line, subtotal, total, and final total all equal 1,900.00. However, there is no external business record proving that 1,900.00 was the agreed amount rather than a later sale edit, and no audit trail proving that 2,300.00 was stale or erroneous. Do not change debt 30 in production.

## 4. Pair 53 / 34

### Sale 34 — stored financial snapshot

| Field | Stored value |
|---|---|
| `id_venta` / `id_cliente` | 34 / 62 |
| `fecha_venta` | `2026-07-16 20:31:05.870568` |
| `canal_venta` / `tipo_entrega` | `LOCAL` / `RETIRO` |
| `estado` / `estado_pago` | `COMPLETADA` / `PENDIENTE` |
| `numero_factura` / `medio_pago` | `F-2026-034` / `NULL` |
| `subtotal` / `descuento_porcentaje` | 1,390.00 / 0.00 |
| `costo_disco` / `costo_envio` / `monto_impuesto` / `otros_costos` | 13.49 / 0.00 / 0.00 / 0.00 |
| `precio_venta` / `total` / `total_final` | 1,390.00 / 1,390.00 / 1,390.00 |
| `ganancia_estimada` | 1,376.51 |
| `monto_pagado` / `monto_deuda` | 0.00 / 790.00 |
| `origen` / `id_pre_venta_origen` | `NULL` / `NULL` |

### Sale-detail reconciliation

| Detail | Product/copy identity | Manual | Qty | Unit price | Line value | Copy snapshot | Acquisition snapshot |
|---:|---|---|---:|---:|---:|---|---|
| 162 | `id_disco=541`; `djfix & Jek` — `unknown species`; code `ED012` | false | 1 | 1,390.00 | 1,390.00 | 4755 | 13.490000 EUR; rate 50.00000000; normalized 674.500000 UYU; source `HISTORICAL_PURCHASE_CONVERSION` |

Detail count and quantity are both 1. The line sum 1,390.00 equals `subtotal`, `total`, and `total_final`; discount is 0.00. Sale-side reconciliation: **RECONCILED**.

### Debt and payment reconciliation

| Field | Stored value |
|---|---|
| `id_deuda` / `id_venta` / `id_cliente` | 53 / 34 / 62 |
| `numero_factura` | `F-2026-034` |
| `monto_total` | 790.00 |
| `monto_pagado_inicial` / `monto_pagado` / `monto_pendiente` | 0.00 / 0.00 / 790.00 |
| `fecha_venta` / `fecha_deuda` / `fecha_ultimo_pago` | 2026-07-16 / 2026-07-16 / `NULL` |
| `fecha_creacion` / `updated_at` | 2026-07-16 20:31:05.901656 / 2026-09-02 23:40:55.616828 |
| `estado_pago` / `activa` | `PENDIENTE` / `true` |
| manual debtor fields / description | all `NULL` |
| `notas` | `djfix` |

Payment query result: zero rows. Expected paid = 0.00; expected pending = 790.00. Debt internal balance: **RECONCILED**. The sale’s cached pending amount is also 790.00, but `sale total - sale paid` is 1,390.00; therefore the sale’s payment-cache arithmetic is not a total reconciliation.

### Relationship and historical evidence

- The debt was created approximately 31 milliseconds after sale 34; IDs and dates link correctly. Exactly one debt references sale 34.
- There is no pre-sale origin and no separate payment ledger record.
- The one catalog line is 1,390.00; there is no stored selected-item or partial-line field supporting 790.00. Selected-items-financed is **NOT SUPPORTED** by the stored detail set.
- The `djfix` note is evidence of a historical note, not proof of the agreed debt amount.
- Same-total creation is **SUPPORTED** by historical code before 2026-07-16; the current divergence is therefore not explained by the normal creation calculation in the available source.
- Independent debt editing is **POSSIBLE** through the historical debt endpoint; the September 2 `updated_at` shows a later debt-row write but does not identify its content.
- A debt-as-pending-only workflow is **POSSIBLE** from the historical endpoint and the matching sale pending cache, but not proven as the business intention for this row.
- Sale-total editing, pre-sale conversion, cancellation unsync, and discount change are **NOT SUPPORTED** by the stored row metadata.

### Classification and correction decision

**INSUFFICIENT EVIDENCE — LEAVE UNCHANGED.** The sale amount is reconciled to its single catalog line, but the database does not establish whether 790.00 was an intentional customer-specific balance, an unrecorded payment/credit, or an incorrectly entered debt total. No correction is safe without an external receipt, operator record, or customer agreement.

## 5. Pair 67 / 48

### Sale 48 — stored financial snapshot

| Field | Stored value |
|---|---|
| `id_venta` / `id_cliente` | 48 / 71 |
| `fecha_venta` | `2026-07-22 17:22:39.615409` |
| `canal_venta` / `tipo_entrega` | `LOCAL` / `RETIRO` |
| `estado` / `estado_pago` | `COMPLETADA` / `PENDIENTE` |
| `numero_factura` / `medio_pago` | `F-2026-048` / `NULL` |
| `subtotal` / `descuento_porcentaje` | 17,573.00 / 0.00 |
| `costo_disco` / `costo_envio` / `monto_impuesto` / `otros_costos` | 161.67 / 0.00 / 0.00 / 0.00 |
| `precio_venta` / `total` / `total_final` | 17,573.00 / 17,573.00 / 17,573.00 |
| `ganancia_estimada` | 17,411.33 |
| `monto_pagado` / `monto_deuda` | 0.00 / 8,640.00 |
| `origen` / `id_pre_venta_origen` | `NULL` / `NULL` |

### Sale-detail reconciliation

All 13 rows are catalog items (`manual_item=false`), quantity 1, with the following stored snapshots:

| Detail | Product identity | Code | Copy snapshot | Unit/line price |
|---:|---|---|---:|---:|
| 177 | Invisible — The Next EP | `COMMUNIQUE011` | 4745 | 1,250.00 |
| 178 | Hackney Electronica — Synaptic Shadows | `DE-338` | 4717 | 1,373.00 |
| 179 | Various — WRECKS303 | `WRECKS303` | 4781 | 1,262.00 |
| 180 | Chris Carrier — Parallel Effect | `MS05` | 4764 | 2,300.00 |
| 181 | BASIC BASTARD — DETROIT EP | `RX15` | 4768 | 1,262.00 |
| 182 | Trailmix — Plum Pudding EP | `OCD.SS-SEVEN` | 6265 | 1,336.00 |
| 183 | Basic Bastard — Basic Bastard Vol. 3 | `DBH-011` | 4746 | 1,200.00 |
| 184 | M.D. — Call & Reponse | `PARTOUT3.04` | 4807 | 1,100.00 |
| 185 | Obelix — Obelix | `WV9009` | 4824 | 1,321.00 |
| 186 | Paradise 3001 — Blue Highway | `NTCLASS004` | 5080 | 927.00 |
| 187 | Rick Wade — Night Station 2 A.m. Detroit | `PND08` | 6272 | 1,459.00 |
| 188 | The Hacker & Rein — We Come Alive | `REKIDS291` | 4729 | 1,299.00 |
| 189 | Betonkust — Tropicana Tracks Two | `ALT025` | 5057 | 1,484.00 |

Total detail count is 13, total quantity is 13, and the line sum is 17,573.00. Every row has `costo_adquisicion_fuente=HISTORICAL_PURCHASE_CONVERSION`, original currency EUR and exchange rate 50.00000000; normalized acquisition cost is null on these rows. The stored discount is 0.00. Sale-side reconciliation: **RECONCILED**.

An independent read-only subset-sum query found no subset of these 13 line values whose sum was exactly 8,640.00. This does not rule out a negotiated amount, credit, exchange, or unrecorded selection; it only rules out an exact stored-price subset explanation.

### Debt and payment reconciliation

| Field | Stored value |
|---|---|
| `id_deuda` / `id_venta` / `id_cliente` | 67 / 48 / 71 |
| `numero_factura` | `NULL` on debt; sale invoice `F-2026-048` |
| `monto_total` | 8,640.00 |
| `monto_pagado_inicial` / `monto_pagado` / `monto_pendiente` | 0.00 / 0.00 / 8,640.00 |
| `fecha_venta` / `fecha_deuda` / `fecha_ultimo_pago` | 2026-07-22 / 2026-07-22 / `NULL` |
| `fecha_creacion` / `updated_at` | 2026-07-22 17:22:39.831393 / 2026-08-19 16:10:50.526571 |
| `estado_pago` / `activa` | `PENDIENTE` / `true` |
| manual debtor fields / description | all `NULL` |
| `notas` | `recibir sonic groov y entregar kruder` |

Payment query result: zero rows. Expected paid = 0.00; expected pending = 8,640.00. Debt internal balance: **RECONCILED**. Sale cached pending is also 8,640.00, but this is not equal to sale total minus sale paid.

### Relationship and historical evidence

- The debt was created approximately 216 milliseconds after the sale. Exactly one debt references sale 48.
- There is no pre-sale origin, no manual sale line, and no payment ledger record.
- The note records a fulfillment or exchange-related instruction, so a custom historical arrangement is **POSSIBLE**, but the note does not state that 8,640.00 is the authoritative financial amount.
- The 13 stored line values reconcile exactly to 17,573.00. Selected-items-financed is **NOT SUPPORTED** as an exact subset by the independent query.
- Same-total creation is **SUPPORTED** by the historical sale/debt code; the available code does not show a normal create path that intentionally creates a debt total of 8,640.00 from a sale total of 17,573.00.
- Independent debt editing is **POSSIBLE**; the later `updated_at` is compatible with a later debt-row write but proves neither the request nor its purpose.
- A debt-as-pending-only workflow is **POSSIBLE**, not proven.
- Sale-total editing, pre-sale conversion, cancellation unsync, and discount change are **NOT SUPPORTED** by the stored metadata.

### Classification and correction decision

**INSUFFICIENT EVIDENCE — LEAVE UNCHANGED.** The large difference and custom note require human business evidence. Neither the sale detail set nor the debt note proves whether the debt is a legitimate negotiated amount, a partial obligation, a trade arrangement, or an entry error. Do not alter either side.

## 6. Pair 89 / 76

### Sale 76 — stored financial snapshot

| Field | Stored value |
|---|---|
| `id_venta` / `id_cliente` | 76 / 37 |
| `fecha_venta` | `2026-08-06 03:52:38.431789` |
| `canal_venta` / `tipo_entrega` | `LOCAL` / `RETIRO` |
| `estado` / `estado_pago` | `CANCELADA` / `PENDIENTE` |
| `numero_factura` / `medio_pago` | `F-2026-076` / `NULL` |
| `subtotal` / `descuento_porcentaje` | 1,350.00 / 0.00 |
| `costo_disco` / `costo_envio` / `monto_impuesto` / `otros_costos` | 349.50 / 0.00 / 0.00 / 0.00 |
| `precio_venta` / `total` / `total_final` | 1,350.00 / 1,350.00 / 1,350.00 |
| `ganancia_estimada` | 731.75 |
| `monto_pagado` / `monto_deuda` | 0.00 / 1,000.00 |
| `origen` / `id_pre_venta_origen` | `NULL` / `NULL` |

### Sale-detail reconciliation

| Detail | Product identity | Manual | Qty | Unit/line price | Copy snapshot | Acquisition snapshot |
|---:|---|---|---:|---:|---:|---|
| 232 | `id_disco=546`; Orlando Voorn — Tronics; code `NTCLASS006` | false | 1 | 1,350.00 | 6264 | 6.990000 EUR; rate 50.00000000; normalized 349.500000 UYU; source `HISTORICAL_PURCHASE_CONVERSION` |

The single line sum 1,350.00 equals `subtotal`, `total`, and `total_final`; discount is 0.00. Sale-side reconciliation: **RECONCILED**.

### Debt and payment reconciliation

| Field | Stored value |
|---|---|
| `id_deuda` / `id_venta` / `id_cliente` | 89 / 76 / 37 |
| `numero_factura` | `NULL` on debt; sale invoice `F-2026-076` |
| `monto_total` | 1,000.00 |
| `monto_pagado_inicial` / `monto_pagado` / `monto_pendiente` | 0.00 / 0.00 / 1,000.00 |
| `fecha_venta` / `fecha_deuda` / `fecha_ultimo_pago` | 2026-08-06 / 2026-08-06 / `NULL` |
| `fecha_creacion` / `updated_at` | 2026-08-06 03:52:39.226587 / 2026-08-06 03:56:23.374679 |
| `estado_pago` / `activa` | `PENDIENTE` / `false` |
| manual debtor fields / description / notes | all `NULL` |

Payment query result: zero rows. Expected paid = 0.00; expected pending = 1,000.00. Debt internal balance: **RECONCILED**. Sale pending cache is also 1,000.00. The sale is canceled and the linked debt is inactive.

### Relationship and historical evidence

- The debt was created approximately 791 milliseconds after the sale and was later updated approximately 3 minutes 44 seconds after creation.
- Customer 37 has several other independent sale/debt rows, but no duplicate debt reference for sale 76. This establishes normal customer history, not the intended amount for this pair.
- The single catalog line is 1,350.00; selected-item financing and discount change are **NOT SUPPORTED** by stored details.
- Cancellation unsync is **SUPPORTED** as a historical state transition: the sale remains canceled with its original total, while the debt remains as an inactive historical row. The timestamps are compatible with a short-lived later write before closure, but do not prove its contents.
- Independent debt editing is **POSSIBLE**; the 3-minute update interval is compatible with an operator action but is not an audit record.
- Payment, pre-sale conversion, and manual-sale explanations are **NOT SUPPORTED**.

### Classification and correction decision

**LEGITIMATE HISTORICAL DIFFERENCE.** Sale 76 is canceled and debt 89 is inactive, with no payments or active balance. The historical cancellation state should be preserved; changing 1,000.00 to 1,350.00 (or changing the sale total) would rewrite a closed historical relationship without proving that either amount was wrong. Leave both rows unchanged.

## 7. Pair 102 / 90

### Sale 90 — stored financial snapshot

| Field | Stored value |
|---|---|
| `id_venta` / `id_cliente` | 90 / 42 |
| `fecha_venta` | `2026-08-18 17:10:08.510659` |
| `canal_venta` / `tipo_entrega` | `INSTAGRAM` / `ENVIO` |
| `estado` / `estado_pago` | `COMPLETADA` / `PENDIENTE` |
| `numero_factura` / `medio_pago` | `F-2026-090` / `TRANSFERENCIA` |
| `subtotal` / `descuento_porcentaje` | 1,290.00 / 0.00 |
| `costo_disco` / `costo_envio` / `monto_impuesto` / `otros_costos` | 667.76 / 260.00 / 0.00 / 0.00 |
| `precio_venta` / `total` / `total_final` | 1,290.00 / 1,290.00 / 1,290.00 |
| `ganancia_estimada` | 622.25 |
| `monto_pagado` / `monto_deuda` | 0.00 / 1,690.00 |
| `origen` / `id_pre_venta_origen` | `NULL` / `NULL` |

### Sale-detail reconciliation

| Detail | Product identity | Manual | Qty | Unit/line price | Copy snapshot | Acquisition snapshot |
|---:|---|---|---:|---:|---:|---|
| 252 | `id_disco=625`; Saharty, mar.c, SoyRuben, Little Sea — Nocturna 001; code `NOC01.1` | false | 1 | 1,290.00 | 6262 | 13.490000 EUR; rate 50.00000000; normalized 667.755000 UYU; source `HISTORICAL_PURCHASE_LANDED_COST` |

The single line sum 1,290.00 equals `subtotal`, `total`, and `total_final`; discount is 0.00. Sale-side reconciliation: **RECONCILED**.

### Debt and payment reconciliation

| Field | Stored value |
|---|---|
| `id_deuda` / `id_venta` / `id_cliente` | 102 / 90 / 42 |
| `numero_factura` | `NULL` on debt; sale invoice `F-2026-090` |
| `monto_total` | 1,690.00 |
| `monto_pagado_inicial` / `monto_pagado` / `monto_pendiente` | 0.00 / 0.00 / 1,690.00 |
| `fecha_venta` / `fecha_deuda` / `fecha_ultimo_pago` | 2026-08-18 / 2026-08-18 / `NULL` |
| `fecha_creacion` / `updated_at` | 2026-08-18 17:10:09.580443 / 2026-08-19 00:50:48.284292 |
| `estado_pago` / `activa` | `PENDIENTE` / `true` |
| manual debtor fields | all `NULL` |
| `descripcion` / `notas` | `Nuñez` / `NOCTURNA + ANTAM` |

Payment query result: zero rows. Expected paid = 0.00; expected pending = 1,690.00. Debt internal balance: **RECONCILED**. Sale pending cache is also 1,690.00, but the sale detail and sale total are 1,290.00.

### Relationship and historical evidence

- The debt was created approximately 1.07 seconds after the sale and updated approximately 7 hours 40 minutes later.
- The debt note `NOCTURNA + ANTAM` is evidence of a custom historical note and could indicate an intended second item or arrangement, but the database contains only one sale detail and does not establish an amount for `ANTAM`.
- The single catalog line reconciles to 1,290.00. A second item, selected-item financing, or an extra 400.00 charge is **NOT PROVEN** by the stored sale data.
- Independent debt editing is **POSSIBLE** through the historical endpoint; the later update is compatible with an operator action but does not prove the field changed.
- Same-total creation is **SUPPORTED** by historical sale/debt code. A pre-sale origin is absent, payment evidence is absent, and discount change is **NOT SUPPORTED**.

### Classification and correction decision

**INSUFFICIENT EVIDENCE — LEAVE UNCHANGED.** The note may explain why the debt is not a simple copy of the sale line, but it is not an authoritative amount or a record of a missing item. Do not change the debt or sale without an invoice, operator record, or customer confirmation.

## 8. Cross-Record Pattern

### What is consistent across all five

- Every debt has an existing sale reference and every reference resolves.
- Every sale has exactly one debt row; no duplicate debt or duplicate sale exists for these IDs.
- All five debts were created within roughly 25 milliseconds to 1.07 seconds after their linked sales, consistent with a sale-linked creation path.
- All five payment queries returned zero rows. The differences are not explained by valid or annulled payment rows.
- All five sale detail sets reconcile exactly to the stored sale `subtotal`, `total`, and `total_final`.
- All five sales store a 0.00 discount percentage. No stored discount explains any mismatch.
- `venta.origen` and `id_pre_venta_origen` are null for all five. Pre-sale conversion is not supported.
- The linked sale’s `monto_deuda` equals the debt’s `monto_pendiente` for pairs 53/34, 67/48, 89/76, and 102/90. Pair 30/25 is the one cache mismatch: 800.00 versus 400.00.

### What the pattern does not prove

The matching pending caches for four pairs show that a historical process propagated the debt balance to the sale cache. They do not prove that the debt balance was correct, that the amount was a selected-item price, or that the amount was an agreed customer obligation.

The historical code timeline is relevant:

- `54d9a5a` (2026-05-26) introduced the sales/debts model.
- `a1db492` (2026-06-24) introduced the debt edit path that accepted an independent `montoTotal`.
- `c099b09` (2026-06-30) retained same-total sale/debt creation behavior.
- `d15fa20` (2026-07-07) introduced copy-based stock and pre-sale/store-expense work, not a debt-total correction.
- `2b911ce` (2026-07-17) added linked debt detail/reporting behavior.
- `65a10a7` (2026-07-22) centralized debt balance handling and synchronized linked sale paid/pending/status caches; it did not create an audit history.
- `3dba984` (2026-07-15) and later payment-ledger commits affect payment recording, but no payment rows are attached to these five debts.

The production database has no financial audit/history/event table matching these rows and no triggers on `venta`, `deuda`, `pago_deuda`, or `detalle_venta`. `deuda.updated_at` identifies that a debt row was written later, but `venta` has no comparable update timestamp and the debt timestamp does not identify the changed columns or user.

## 9. Current Reporting Impact

- Active debt summaries include debts 53, 67, and 102 because they are active and linked to non-canceled sales. They use the debt rows’ stored/recomputed totals and pending balances, so those amounts affect active customer debt totals.
- Debt 30 is inactive and debt 89 is inactive; both are excluded from the current active debt list and active debt summary.
- Sale 76 is canceled and is excluded from the current sales-book sale movements.
- The sales book and monthly income code use sale movements and the debt’s `monto_pagado_inicial` for linked-sale original income. Since all five have no payment rows, only pair 30 has an initial paid amount of 1,500.00; the other four have 0.00.
- No payment-ledger income or reversal movement is generated by these five debts because no `pago_deuda` row exists.
- Historical views that directly display debt totals, customer debt balances, or compare linked sale and debt totals can show the divergences.
- The sale detail/copy snapshots are independent historical records. A debt-only correction, if ever authorized, would not by itself restore or remove inventory; nevertheless, no debt-only correction is authorized by this audit.
- Changing any of the five debt totals would change active debt balances or historical detail without changing the reconciled sale line evidence. That is why the audit leaves the four uncertain pairs unchanged and preserves the canceled pair.

## 10. Proposed Corrections

No correction is proposed for execution.

The required safe-correction categories were applied conservatively. No pair qualified as `SAFE TO CORRECT — SALE TOTAL AUTHORITATIVE` or `SAFE TO CORRECT — DEBT TOTAL AUTHORITATIVE`, because the database has no authoritative business-intent record for the divergent amount. Consequently, no SQL block is included. This avoids presenting unapproved SQL for a production financial row.

If an authorized operator later supplies external evidence, the correction must be designed from that evidence and validated against the payment ledger, sale caches, debt status, reporting behavior, and inventory history in a separate controlled change. The current report is not authorization for that work.

## 11. Records That Must Remain Unchanged

Until authoritative external evidence is supplied, retain unchanged:

- `venta.id_venta=25` and `deuda.id_deuda=30`, including sale total 1,900.00, debt total 2,300.00, initial paid 1,500.00, and the manual-item detail snapshot.
- `venta.id_venta=34` and `deuda.id_deuda=53`, including the 1,390.00 catalog line and debt total 790.00.
- `venta.id_venta=48` and `deuda.id_deuda=67`, including all 13 catalog detail/copy snapshots, sale total 17,573.00, debt total 8,640.00, and the custom note.
- `venta.id_venta=76` and `deuda.id_deuda=89`, including the canceled sale and inactive debt state.
- `venta.id_venta=90` and `deuda.id_deuda=102`, including the single 1,290.00 catalog line and the notes `Nuñez` / `NOCTURNA + ANTAM`.
- All five `fecha_creacion`, `updated_at`, payment-ledger absence, copy snapshots, acquisition-cost snapshots, sale totals, debt totals, statuses, and relationship IDs.

## 12. Production Recommendation

Keep production unchanged. Do not normalize these five pairs to the newly enforced linked-debt invariant based only on current-code expectations. The audit establishes historical divergence and identifies a plausible legacy debt-edit mechanism, but it does not establish which side represents the agreed business amount for four pairs. The canceled pair should remain a closed historical state.

Required next evidence before any correction would be one of: an invoice/receipt, an operator-maintained debt record, a customer agreement, or another contemporaneous business record that identifies the intended amount and whether it represented the full sale, a partial obligation, an exchange, or a separate item.

**READ-ONLY AUDIT COMPLETE — NO PRODUCTION CHANGES MADE.**
