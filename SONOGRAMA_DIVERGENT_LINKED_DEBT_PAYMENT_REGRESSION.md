# Sonograma — Divergent Linked-Debt Payment Regression

## 1. Risk Being Tested

This local regression test verifies that a pre-existing linked debt whose historical total differs from its sale total can receive and reverse a payment without being normalized. It also confirms that the direct linked-debt total invariant continues to reject direct edits.

No production host, database, container, or deployment workflow was accessed.

## 2. Historical Divergent Fixture

The new fixture intentionally models the historical divergence:

| Record | Value |
|---|---:|
| Venta `totalFinal` | 1390 |
| Venta `montoPagado` | 0 |
| Venta `montoDeuda` | 790 |
| Venta `estadoPago` | PENDIENTE |
| Linked Deuda `montoTotal` | 790 |
| Linked Deuda `montoPagadoInicial` | 0 |
| Linked Deuda `montoPagado` | 0 |
| Linked Deuda `montoPendiente` | 790 |
| Linked Deuda `activa` | true |

The fixture does not normalize `Venta.totalFinal` to the debt total or change the debt total to the sale total.

## 3. Payment Result

Registering a valid payment of 300 succeeded.

- Deuda: `montoTotal=790` preserved; `montoPagado=300`; `montoPendiente=490`.
- Venta: `totalFinal=1390` preserved; `montoPagado=300`; `montoDeuda=490`; `estadoPago=PARCIAL`.
- The payment path did not invoke direct debt-total validation.

## 4. Reversal Result

Reversing the same payment through `eliminarPago` succeeded.

- Deuda: `montoTotal=790` preserved; `montoPagado=0`; `montoPendiente=790`; `estadoPago=PENDIENTE`.
- Venta: `totalFinal=1390` preserved; `montoPagado=0`; `montoDeuda=790`; `estadoPago=PENDIENTE`.
- The payment row remained persisted, was saved again with `anulado=true`, and was not deleted.

## 5. Direct Edit Invariant

Attempting to change the linked debt total from 790 to 1000 was rejected with the existing linked-debt invariant. The debt and sale totals remained unchanged.

## 6. Sale Edit Synchronization

The existing `edicionDeVentaSincronizaElNuevoTotalConLaDeudaVinculada` test continues to pass. It verifies that the authoritative sale-edit synchronization path may update the linked debt total when the sale total is intentionally changed.

## 7. Code Changes

Only tests were added to:

`sonograma-backend/src/test/java/com/sonograma/service/DeudaServiceTest.java`

Added coverage for:

- payment and logical reversal against a divergent linked debt;
- preservation of both historical totals and payment-related caches;
- direct 790→1000 total-edit rejection.

No production application code was modified.

## 8. Test Results

- Targeted: `mvn -q -Dtest=DeudaServiceTest test` — 22 tests, 0 failures, 0 errors, 0 skipped.
- Complete backend suite: `mvn -q test` — 390 tests, 0 failures, 0 errors, 1 skipped.
- `git diff --check` — passed.

## 9. Production Impact

None. This task was local-only. There was no production connection, deployment, migration, database write, service restart, or production data modification.

## 10. Deployment Recommendation

The targeted historical-divergence regression is covered and passes. Existing direct-edit protection and sale-authoritative synchronization remain covered and passing. The change is suitable to proceed through the normal reviewed deployment process; this task itself performed no deployment.

PASS — PRE-EXISTING DIVERGENT LINKED DEBTS ARE SAFE FOR DEPLOYMENT
