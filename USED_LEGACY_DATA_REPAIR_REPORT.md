# USED Legacy Data Repair Report

Repair date: 2026-10-08
Scope: Phase G2, real Sonograma production database

## 1. Executive Summary

The authorized Phase G2 repair completed successfully against the real production PostgreSQL database. One guarded serializable transaction populated only approved `disco_qr_copy.precio_venta` and `disco_qr_copy.condicion_fisica` fields.

Results:

- 648 distinct physical copies changed;
- 629 prices populated;
- 647 physical conditions populated;
- 623 Category A copies received both fields;
- 5 Category B copies received both fields;
- 1 Category D copy received price only;
- 19 Category D copies received condition only;
- 9 Category C copies received no writes;
- no unsupported Category D field changed.

The transaction committed at `2026-10-08T11:39:32.011078Z`. A second execution completed with zero writes, proving operational idempotency.

## 2. Authorization Scope

The write authorization was limited to the approved Phase G1 evidence manifest:

- Category A: price and condition;
- Category B: proven creation-group price and condition;
- Category D: only the individually proven field;
- Category C: no modification;
- Category E: none.

The only columns in the sole production `UPDATE` statement were:

- `disco_qr_copy.precio_venta`;
- `disco_qr_copy.condicion_fisica`.

No parent product field, identity field, lifecycle field, provenance field, aggregate, QR, sale, debt, pre-sale, or import-history field was authorized.

## 3. G1 Revalidation

Immediately before the production transaction, the Phase G1 read-only audit was rerun through SSH, the private PostgreSQL container, and an explicit read-only transaction.

The rerun confirmed exactly:

| Check | Result |
|---|---:|
| Target copies | 657 |
| Products | 651 |
| Category A | 623 |
| Category B | 5 |
| Category C | 9 |
| Category D | 20 |
| Category E | 0 |
| Recoverable prices | 629 |
| Recoverable conditions | 647 |
| Both fields recoverable | 628 |
| Copies requiring some manual review | 29 |

The normalized candidate-array SHA-256 was identical for the approved and freshly revalidated manifests: `ded6e8069b27ddd84d7ca9b61224b21b56772caf1999c2cdbf2d60b082c3ac33`.

No production drift was detected, so execution was allowed to continue.

## 4. Manifest Integrity

The approved manifest passed local and in-transaction validation:

- valid JSON object and candidate array;
- 657 candidates and 657 unique copy IDs;
- 651 unique products;
- only A/B/C/D/E category values;
- exact category and recoverability totals;
- nonblank evidence source and object-valued evidence identifiers for every candidate;
- no approved fields on Category C;
- exactly one approved field on every Category D candidate;
- both approved fields on every Category A/B candidate.

The repair plan was independently cross-joined to the approved manifest inside the transaction. Copy ID, product ID, category, evidence type, evidence identifiers, and every new value had to match exactly.

## 5. Manifest SHA-256

Approved manifest:

`d69d3539b9d9827dd319146d2c92519867b106d1cb2f69a46c50c61f2fdda6a9  used-copy-reconciliation-candidates.json`

Supporting artifact checksums:

- repair plan: `d0c5a0eca2d9aae8512eb4cf4a6285470dbfa0c962f496ded6be8cc9e65ca707`;
- before image: `5f619f42e08d635df66b941b74b522802e9fda3e3bfd42dd9447d84e11d6c435`;
- after image: `0cf386b805df24ee680fd857f62280e021fe4e2dedb198c9afeb1dde66ae1b60`;
- first execution result: `8f889c2c84791cb2656f1e9846fa42c191710b9cf3b471dcf8c53a62876d8d53`;
- idempotency result: `116c239b5aa08f573cecc96073b4ad9b5586c6b6483bf3fb1be796ceacfdf989`.

## 6. Repair Plan

`used-copy-repair-plan.json` contains 648 unique copy records and only authorized mutation fields:

- 629 records with `newPrice`;
- 647 records with `newCondition`;
- 628 records with both;
- 623 Category A records;
- 5 Category B records;
- 20 Category D records;
- no Category C or E records.

Each record contains copy/product identity, category, old values, only the approved new fields, evidence type, and evidence identifiers. All old price and condition values were null in the before image.

## 7. Pre-Write Guards

After locking only the 648 planned `disco_qr_copy` rows, the transaction revalidated:

- all 657 manifest rows and all 648 planned rows still existed;
- every copy still belonged to the expected active USED parent;
- every copy remained AVAILABLE;
- every planned field was still null/non-positive or blank;
- every unsupported Category D field still equaled its before value;
- every exact import row/job/source-row identifier still existed and matched the planned product/value;
- exact-operation timestamps and uniqueness still held;
- every group evidence row set still exactly matched the approved row IDs and source-row numbers;
- every approved group field remained invariant across its operation;
- no new source ambiguity had appeared.

Any failure would have raised an exception under `ON_ERROR_STOP` and rolled back the entire transaction.

## 8. Before Image

`used-copy-repair-before.json` was captured in a fresh read-only transaction before the write and made locally read-only.

It contains all 648 targets with copy/product identity, copy number, current price/condition, state, evidence identifiers, QR fingerprint, non-commercial fingerprint, timestamps, and batch ID. It contains no full QR values, customer data, credentials, or secrets.

Before-state summary:

- 648/648 targets existed and were AVAILABLE;
- 0 had a non-null price;
- 0 had a non-null condition;
- global AVAILABLE physical rows: 936;
- physical states: 936 AVAILABLE, 131 SOLD;
- aggregate drift: 0;
- exact and normalized QR duplicate groups: 0.

## 9. Transaction Execution

The repair ran through the existing private path:

`local runner -> SSH Lightsail -> docker exec sonograma-postgres -> psql`

Transaction properties:

- one explicit `SERIALIZABLE` transaction;
- `statement_timeout = 45s`;
- `lock_timeout = 3s`;
- `idle_in_transaction_session_timeout = 90s`;
- exact target-row locks only;
- one `UPDATE` statement;
- all post-update verification before `COMMIT`.

The transaction committed at `2026-10-08T11:39:32.011078Z` with 648 affected rows.

## 10. Category A Repair

All **623 Category A** copies received their manifest-proven exact price and physical condition:

- price fields populated: 623;
- condition fields populated: 623;
- failed evidence or row guards: 0.

## 11. Category B Repair

All **5 Category B** copies received their creation-group-proven price and condition:

- price fields populated: 5;
- condition fields populated: 5;
- failed group membership, row-set, or uniformity guards: 0.

No current parent value was used as repair input.

## 12. Category D Partial Repair

All **20 Category D** copies followed field-level authorization:

- copy 14173 received its proven price only;
- 19 copies received their proven condition only;
- unsupported price fields changed: 0;
- unsupported condition fields changed: 0.

## 13. Category C Preservation

The nine Category C copy rows were excluded from the repair plan. An in-transaction fingerprint over their identity, state, QR, price, condition, timestamps, and batch link was identical before and after the `UPDATE`.

Category C rows changed: **0**.

## 14. Post-Update Verification

Before commit, the transaction proved:

- all 648 planned rows still existed;
- all 629 planned prices equaled their evidence values;
- all 647 planned conditions equaled their evidence values;
- all Category C fields remained unchanged;
- all unsupported Category D fields remained unchanged;
- target non-commercial fingerprints were unchanged;
- QR values and copy numbers were unchanged;
- physical states and state distribution were unchanged;
- parent rows, including commercial fields and aggregate quantities, were unchanged;
- sale, debt, pre-sale, and import-history hashes were unchanged;
- AVAILABLE physical count remained 936;
- aggregate drift remained 0;
- exact and normalized QR duplicate groups remained 0.

The transaction would not have committed if any invariant differed.

## 15. Commit / Rollback Result

Result: **COMMITTED**.

- Real production database modified: yes, under explicit authorization;
- rows affected: 648;
- price fields written: 629;
- condition fields written: 647;
- transaction completion: `2026-10-08T11:39:32.011078Z`;
- partial commit: no;
- rollback required: no.

## 16. After Image

`used-copy-repair-after.json` was captured after commit using a new read-only transaction and made locally read-only.

It contains the same 648 targets and the same non-commercial/fingerprint fields as the before image. Results:

- 629 targets have a positive repaired price;
- 647 targets have a repaired condition;
- all 648 remain AVAILABLE;
- aggregate drift remains 0;
- QR duplicate groups remain 0.

## 17. Before-vs-After Diff

The structured comparison produced:

| Difference | Count |
|---|---:|
| Price changes | 629 |
| Condition changes | 647 |
| Non-commercial fingerprint changes | 0 |
| QR fingerprint changes | 0 |
| State changes | 0 |
| Product-link changes | 0 |
| Copy-number changes | 0 |
| Unsupported price changes | 0 |
| Unsupported condition changes | 0 |
| Approved-value mismatches | 0 |

No database-managed `updated_at` change occurred because the direct SQL update did not include that column and no database trigger changed it.

## 18. Production Diagnostic After Repair

The full read-only production Stock diagnostic was rerun after commit.

- active products: 995;
- total physical rows: 1,067;
- AVAILABLE physical rows: 936;
- SOLD physical rows: 131;
- unexpected states: 0;
- AVAILABLE USED copies: 810;
- USED copies with valid positive exact price: 782;
- USED copies still missing exact price: 28;
- USED copies still missing condition: 10;
- aggregate drift products: 0;
- exact QR duplicate groups: 0;
- normalized QR duplicate groups: 0.

The separate post-repair USED diagnostic found exactly 29 remaining review copies: Category C = 9 and Category D = 20. The unrelated findings remain unchanged and untouched: 9 stale parent QR aliases, 8 missing completed-sale snapshot physical rows, and 63 SOLD rows without a non-cancelled modern snapshot reference.

## 19. Stock Valuation Before/After

| Projected USED known UYU | Amount |
|---|---:|
| Before | 190,591.80 |
| After | 807,129.30 |
| Increase | 616,537.50 |

The increase exactly equals the sum of the 629 manifest-approved prices. No value was adjusted to force a target total. Twenty-eight AVAILABLE USED copies remain excluded from projected USED value because their exact price is still unknown.

## 20. Idempotency Verification

The same runner, approved manifest, and repair plan were executed a second time after the successful commit.

Result at `2026-10-08T11:40:26.410581Z`:

- mode: `ALREADY_APPLIED_ZERO_WRITES`;
- rows affected: 0;
- price fields written: 0;
- condition fields written: 0;
- all protected invariants unchanged;
- missing price remained 28;
- missing condition remained 10.

The process rejects mixed partial state; it permits only the complete pre-repair state or the complete already-applied state.

## 21. Files Created

Required files:

- `scripts/production-used-repair.sql`;
- `scripts/run-production-used-repair.sh`;
- `used-copy-repair-plan.json`;
- `used-copy-repair-before.json`;
- `used-copy-repair-after.json`;
- `USED_LEGACY_DATA_REPAIR_REPORT.md`.

Additional local audit evidence:

- `used-copy-repair-execution.json`;
- `used-copy-repair-idempotency.json`;
- `used-copy-reconciliation-revalidation.json`;
- `production-diagnostic-results-after-used-repair.json`;
- `used-copy-post-repair-diagnostic.json`.

Production JSON artifacts are locally ignored by Git. The SQL and runner contain no production password or other credential.

## 22. Commands Executed

Principal commands:

```bash
scripts/run-production-used-evidence-audit.sh used-copy-reconciliation-revalidation.json
scripts/run-production-used-repair.sh before used-copy-repair-before.json
scripts/run-production-used-repair.sh execute used-copy-repair-execution.json
scripts/run-production-used-repair.sh after used-copy-repair-after.json
scripts/run-production-stock-diagnostic.sh production-diagnostic-results-after-used-repair.json
scripts/run-production-used-evidence-audit.sh used-copy-post-repair-diagnostic.json
scripts/run-production-used-repair.sh execute used-copy-repair-idempotency.json
```

Local `jq` checks validated manifest/plan shape and counts, candidate equivalence, before/after differences, transaction results, diagnostics, and the zero-write rerun. `shasum -a 256` produced the recorded artifact checksums.

## 23. Safety Confirmation

- Real production database modified: **yes**.
- Modification explicitly limited to approved copy price/condition fields: **yes**.
- Category C rows changed: **0**.
- Unsupported Category D fields changed: **0**.
- QR values changed: **0**.
- Physical states changed: **0**.
- Copy numbers/product links changed: **0**.
- Parent commercial fields changed: **0**.
- Aggregate quantities changed: **0**.
- Sales, debts, pre-sales, or import rows changed: **0**.
- Aggregate drift after repair: **0**.
- QR duplicate groups after repair: **0**.
- Application deployed: **no**.
- Application restarted: **no**.
- Migration created: **no**.

Production remains at commit `977a9dd7594a74b905639ce9a2e72725e3567999`. Backend and PostgreSQL remained healthy; container start timestamps predate this repair. Persisting values in existing nullable columns is backward compatible with that deployed version and with the pending Phase A-E code.

## 24. Remaining Manual Review Population

Twenty-nine AVAILABLE USED copies still require at least one manual decision:

- 9 Category C copies lack both authoritative price and condition;
- 19 Category D copies now have condition but still lack price;
- 1 Category D copy now has price but still lacks condition.

Therefore production now has:

- 28 AVAILABLE USED copies without exact price;
- 10 AVAILABLE USED copies without physical condition;
- 9 copies missing both.

These values must remain null until separately reviewed or physically inspected. Parent values remain context only, not authoritative backfill inputs.

## 25. Recommendation

Treat Phase G2 as complete. Do not broaden this repair or reuse parent values for the remaining 29 copies. A future explicitly authorized manual-review phase may resolve those records individually.

Handle the 9 stale parent QR aliases, 8 missing sale-snapshot physical rows, and 63 unreferenced SOLD rows only in separate evidence-led phases. No application deployment is required for this data repair.

USED REPAIR STATUS: SUCCESS
