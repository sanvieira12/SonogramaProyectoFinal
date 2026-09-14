# Sonograma — Venta 25 / Deuda 30 Investigation

## 1. Safety Confirmation

This investigation used only read-only local Git inspection, remote file inspection, shell metadata commands, and PostgreSQL `SELECT` queries through the existing Lightsail SSH connection.

No `UPDATE`, `INSERT`, `DELETE`, migration, deployment, container restart, service restart, production-file modification, repair, or synthetic data creation was performed.

Sensitive credentials were not printed. The SSH private key contents were not read.

## 2. Venta 25 Snapshot

Production row: `venta.id_venta = 25`.

| Field | Value |
|---|---|
| `id_cliente` | 43 |
| Client | Gonzalo Pereyra |
| `fecha_venta` | `2026-07-06 21:43:53.954105` |
| `canal_venta` | `LOCAL` |
| `precio_venta` | `1900.00` |
| `subtotal` | `1900.00` |
| `descuento_porcentaje` | `0.00` |
| `monto_impuesto` | `0.00` |
| `costo_disco` | `0.00` |
| `costo_envio` | `0.00` |
| `otros_costos` | `0.00` |
| `total` | `1900.00` |
| `total_final` | `1900.00` |
| `monto_pagado` | `1500.00` |
| `monto_deuda` | `400.00` |
| `estado_pago` | `PARCIAL` |
| `estado` | `COMPLETADA` |
| `numero_factura` | `F-2026-025` |
| `numero_recibo` | `NULL` |
| `tipo_entrega` | `RETIRO` |
| `medio_pago` | `TRANSFERENCIA` |
| `observaciones` | `NULL` |
| `origen` | `NULL` |
| `id_pre_venta_origen` | `NULL` |

The production `venta` schema has no `created_at`, `updated_at`, `descuento_monto`, `recargo`, or `tipo` columns. These values therefore cannot be reported as stored fields.

## 3. Sale Item Reconciliation

Sale 25 has one attached line:

| Field | Value |
|---|---|
| `id_detalle` | 93 |
| `id_disco` / product | `NULL` / manual item |
| Artist snapshot | `NULL` |
| Album/description snapshot | `ninja tools vol 4` |
| `cantidad` | 1 |
| `precio_unitario` | `1900.00` |
| Line subtotal | `1900.00` |
| `manual_item` | `true` |
| Copy/stock snapshot | `NULL` |
| Stored line discount/final amount | No separate field; line amount is `1900.00` |

Aggregate result:

```text
item_rows = 1
total_quantity = 1
line_items_sum = 1900.00
```

Reconciliation:

- Line sum `1900.00` equals `venta.subtotal` `1900.00`.
- Line sum `1900.00` equals `venta.total` `1900.00`.
- Line sum `1900.00` equals `venta.total_final` `1900.00`.
- `descuento_porcentaje` is `0.00`; there is no production evidence of a stored `400.00` discount.
- There is no catalog/product join because `id_disco` is `NULL`; this was recorded as a manual sale item.

The sale-side financial evidence consistently supports `1900.00` as the sale total.

## 4. Deuda 30 Snapshot

Production row: `deuda.id_deuda = 30`.

| Field | Value |
|---|---|
| `id_venta` | 25 |
| `id_cliente` | 43 |
| Client | Gonzalo Pereyra |
| `numero_factura` | `F-2026-025` |
| `monto_total` | `2300.00` |
| `monto_pagado_inicial` | `1500.00` |
| `monto_pagado` | `1500.00` |
| `monto_pendiente` | `800.00` |
| `fecha_venta` | `2026-07-06` |
| `fecha_deuda` | `2026-07-06` |
| `fecha_ultimo_pago` | `NULL` |
| `fecha_creacion` | `2026-07-06 21:43:53.979039` |
| `updated_at` | `2026-07-17 02:29:51.934785` |
| `estado_pago` | `PARCIAL` |
| `activa` | `false` |
| `tipo` | No such production column |
| `descripcion` | `NULL` |
| `notas` | `NULL` |
| Manual debtor fields | `NULL` |

The debt was created approximately 25 milliseconds after the sale row, which is consistent with creation during the same sale transaction. Its later `updated_at` is evidence of a later write to the debt row, but does not identify the writer or prove the changed field.

## 5. Payment History

`SELECT COUNT(*) FROM pago_deuda WHERE id_deuda = 30` returned **0**.

The detailed payment query also returned **0 rows**. Therefore:

- No later payment is currently registered for debt 30.
- The `1500.00` paid amount is entirely represented by `monto_pagado_inicial`.
- No payment reversal or payment deletion can be established from the current payment rows.

## 6. Current Creation Logic

The current production checkout is commit `f50e6ab3dc7d4b6452d94e5cd72cf427c45d4fd5`, matching the local branch state.

Current relevant code:

- `sonograma-backend/src/main/java/com/sonograma/service/VentaService.java:82` — `registrarVenta`.
  - For detail sales, it computes the discounted net price before calling `CostosVentaService`.
  - It sets `Venta.total` and `Venta.totalFinal` from `costos.getTotalFinal()`.
  - It computes `montoPagado` and `montoDeuda` from that same total.
  - It calls `deudaService.sincronizarVenta(...)` with `costos.getTotalFinal()` at lines 176–177.
- `VentaService.java:215` — `actualizarVenta`.
  - It recomputes the sale total from the edited details and discount.
  - It sets the sale total fields and calls the same debt synchronization method at lines 309–310.
- `sonograma-backend/src/main/java/com/sonograma/service/DeudaService.java:146` — `sincronizarVenta`.
  - It sets `Deuda.montoTotal` from the passed sale total.
  - It sets the initial paid amount and recalculates the debt balance.
  - It synchronizes the linked sale’s paid, pending, and payment-status caches.

Therefore, the normal current sale-create and sale-edit paths do not explain a debt total of `2300.00` beside a sale total of `1900.00`; both sides receive the same computed sale total.

Discount handling is also explicit: detail-sale discounts are applied to the item subtotal before `costos.getTotalFinal()` is produced. Sale 25 stores `descuento_porcentaje = 0.00`, so no stored discount explains the `400.00` difference.

The current debt edit path is separate:

- `DeudaController.java:55` exposes `PUT /deudas/{idDeuda}`.
- `DeudaService.java:130` — `actualizar` accepts an active debt and allows `montoTotal` to be supplied.
- `DeudaService.java:431–454` — `aplicarRequest` sets `montoTotal`, accepts the existing paid amount, and recalculates `montoPendiente`.
- `DeudaService.java:609–619` — current recalculation updates linked sale paid/debt/status fields, but does not set `Venta.total` or `Venta.totalFinal`.

Thus, current code can still create a linked debt-total-versus-sale-total mismatch if an operator edits `montoTotal` through the debt endpoint. Debt 30 is inactive now and cannot be edited through the current active-debt guard.

## 7. Historical Git Evidence

The closest relevant code before the July 6 creation was commit `c099b09` from June 30, 2026. The original debt implementation used:

```java
Venta.total       = costos.getTotalFinal();
Venta.totalFinal  = costos.getTotalFinal();
Venta.montoDeuda  = costos.getTotalFinal() - montoPagado;
Deuda.montoTotal  = costos.getTotalFinal();
Deuda.montoPagado = montoPagado;
Deuda.montoPendiente = montoDeuda;
```

The same-total behavior is visible in the historical `VentaService` at the debt builder around lines 156–166 of `c099b09`. The older initial sales/debt implementation in `54d9a5a` also used the computed cost total for the debt total.

The historical `DeudaService.actualizar` in `c099b09` accepted a new `montoTotal`, recalculated only the debt row’s pending amount, and saved the debt. It did not synchronize the linked `Venta` total fields. The `PUT /deudas/{idDeuda}` controller route was already present.

Relevant timeline:

- Sale/debt creation: July 6, 2026.
- `d15fa20` on July 7: copy stock/pre-sale/store-expense changes, not evidence of a debt-total correction.
- `3dba984` on July 15: payment ledger/deletion changes.
- `2b911ce` on July 17: reporting and linked-sale detail exposure, not a financial total correction.
- Migration 029 was introduced on July 22; it backfilled `monto_pagado_inicial` and normalized balances but did not change `monto_total`.

Git history therefore shows a credible historical manual-debt-edit mechanism, but no commit proves that it was used for debt 30. It does not show a creation-path bug that would intentionally pass `2300.00` to Deuda and `1900.00` to Venta.

The arithmetic `2300.00 - 400.00 = 1900.00` is compatible with a stale or independently edited debt total, but is not proof of a `$400.00` discount. The actual sale stores no such discount.

## 8. Neighboring Records

The nearby July 1–10 sample contained only one partial-payment sale: sale 25. Nearby sales 20–24 were fully paid; sale 26 was fully paid before cancellation.

A broader read-only scan found **5 linked debt/sale total differences** in production, not just debt 30:

| Debt | Sale | Debt total | Sale total | Paid | Pending | Debt active | Sale state |
|---:|---:|---:|---:|---:|---:|---|---|
| 30 | 25 | 2300.00 | 1900.00 | 1500.00 | 800.00 vs 400.00 | false | COMPLETADA |
| 53 | 34 | 790.00 | 1390.00 | 0.00 | 790.00 = 790.00 | true | COMPLETADA |
| 67 | 48 | 8640.00 | 17573.00 | 0.00 | 8640.00 = 8640.00 | true | COMPLETADA |
| 89 | 76 | 1000.00 | 1350.00 | 0.00 | 1000.00 = 1000.00 | false | CANCELADA |
| 102 | 90 | 1690.00 | 1290.00 | 0.00 | 1690.00 = 1690.00 | true | COMPLETADA |

Only debt 30 was returned by the original Venta/Deuda cache-mismatch query because the other four have matching paid/pending/status caches despite differing total fields. The phenomenon is therefore not isolated at the raw total-field level, although debt 30 is the only currently detected mismatch in the paid/pending cache reconciliation.

For the five records, item aggregates were respectively `1900.00`, `1390.00`, `17573.00`, `1350.00`, and `1290.00`, matching their corresponding sale totals. This supports the sale-side totals, but does not establish why each debt total differs.

## 9. Audit / Log Evidence

Production metadata checks found:

- No audit/history/event/log table covering financial row changes.
- No database triggers on `venta`, `deuda`, `pago_deuda`, or `detalle_venta`.
- No non-system database functions indicating financial auditing.
- `deuda.updated_at` exists, but `venta` has no `updated_at` column.
- Debt 30’s `updated_at` is July 17, 2026, while its creation timestamp is July 6, 2026.

Available backend log files cover September 7–14, 2026 only. Searches of current and compressed logs returned no match for debt 30 or sale 25, and Docker logs had no retained output for July 6–18. No historical request/user evidence is available.

There is therefore no audit trail capable of proving whether debt 30 was edited manually, changed by a historical endpoint, or altered by another process.

## 10. Root Cause

Evidence rules out or fails to prove the following:

- A stored `$400.00` sale discount: not supported; sale discount is `0.00`, and the item sum is `1900.00`.
- A current normal sale-creation calculation bug: not supported; current and pre-July-6 creation paths use the same computed total for both records.
- A payment-driven explanation: not supported; debt 30 currently has zero payment rows and no later payment date.
- A confirmed manual edit: plausible because historical debt editing accepted independent totals and debt 30 has a later `updated_at`, but no audit/log evidence proves the action.

The most plausible mechanism is a historical debt-side edit or stale debt total, but the exact root cause is not provable from the available data.

## 11. Current-Code Risk

Current normal sale creation and sale editing synchronize the debt total from the computed sale total, so they are not currently capable of producing this specific divergence by themselves.

However, the current `PUT /deudas/{idDeuda}` path still accepts a new `montoTotal` for active debts and recalculates linked sale paid/pending/status caches without changing `Venta.total` or `Venta.totalFinal`. That path can still produce a debt-total-versus-sale-total mismatch for an active linked debt.

Debt 30 is inactive, has no later payment, and is not editable through the current active-debt guard.

## 12. Reporting Impact

- Debt 30 is inactive (`activa = false`), so current active-debt lists and debt summaries exclude it.
- Sale 25 remains `COMPLETADA` and contributes its sale-side values: total `1900.00`, paid `1500.00`, debt `400.00`.
- There are no `PagoDeuda` rows for debt 30, so it contributes no payment-ledger income or reversal movement.
- The mismatch can affect any historical/admin view that directly reads inactive debt rows or compares debt totals to sale totals.
- Based on the current code, changing only debt financial fields would not restore or remove stock/QR inventory; the sale item and its manual-item history would remain unchanged.
- It could change debt/customer-history amounts or any report that includes inactive debt movements. The current active debt dashboard is not affected by debt 30’s inactive row, while sales history/Libro use the sale-side row and are not changed by a debt-only correction.

## 13. Recommended Correction

Do not modify the row during this investigation.

The sale side is strongly supported by three independent values — manual line sum, subtotal, and total/final — all equal to `1900.00`. The debt-side `2300.00` has no attached payment, item, discount, note, or audit evidence supporting it. If the business owner confirms that the agreed sale amount was `1900.00`, the future controlled correction should be debt-only: preserve `monto_pagado_inicial = 1500.00`, set the debt total/pending values consistently with the sale (`1900.00` / `400.00`), and retain the existing sale and inventory history.

Because the historical business intent is not recorded, the safe recommendation now is to leave debt 30 unchanged, document it, and obtain an operator/customer record or other authoritative evidence before any correction. No SQL write proposal was executed.

## 14. Final Classification

ROOT CAUSE NOT PROVABLE FROM AVAILABLE DATA
