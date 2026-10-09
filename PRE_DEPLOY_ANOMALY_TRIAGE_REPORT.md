# Phase G3.1 — Refreshed Pre-Deploy Anomaly Triage

Audit date: 2026-10-08
Production: AWS Lightsail / `sonograma_db`
Deployed revision: `977a9dd7594a74b905639ce9a2e72725e3567999`

## 1. Executive Summary

The production-data portion of G3.1 is complete. A fresh target-scoped baseline found all nine authorized parent QR aliases noncanonical under the actual pending Phase D code. One guarded serializable transaction changed only `disco.codigo_qr` on those nine parent rows. No physical QR, physical state, copy number, quantity, price, condition, sale, snapshot, debt, pre-sale, or import row changed. The second execution wrote zero rows.

The eight missing snapshot identities are all classified S1: historical identity loss safely contained. The 63 SOLD rows without a non-cancelled modern snapshot are classified V1=30, V2=3, V3=0, V4=30. Every row remains SOLD, cannot be sold by exact QR, and contributes zero to Stock valuation. No historical row was repaired or inferred.

The production schema and startup behavior are compatible with the audited pending workspace: there are no pending custom-ledger migrations, production uses `ddl-auto=validate`, startup repair/demo runners are disabled, and nullable USED price/condition plus historical snapshot gaps are supported.

Deployment is nevertheless blocked as a release operation because there is no intended deployment commit. Local `HEAD` and `origin/main` remain the already-deployed `977a9dd…`; approved Phase A-E changes exist only in a dirty working tree, including two untracked runtime Java files. The exact revision requested for deployment therefore does not exist and cannot be named or pulled by `deploy/deploy.sh`.

## 2. Concurrent Production Activity Policy

G3.1 used stable IDs and target fingerprints. Unrelated legitimate production activity was allowed. The run would stop only for a target change, aggregate drift, QR duplication, invalid lifecycle state, missing-snapshot target change, SOLD-target change, or deployment-compatibility change.

Between the prior G3 snapshot and the G3.1 baseline, one legitimate sale changed AVAILABLE from 937 to 936 and SOLD from 131 to 132 while total physical rows stayed 1,068. The nine alias targets, eight missing identities, 63 SOLD targets, aggregate invariant, and QR invariants remained stable, so the refreshed policy allowed work to continue.

## 3. Fresh Baseline

Baseline generated at `2026-10-08T18:10:25Z` in an explicit read-only transaction:

| Metric | Result |
|---|---:|
| Active products | 995 |
| Total physical rows | 1,068 |
| AVAILABLE | 936 |
| SOLD | 132 |
| REMOVED | 0 |
| Aggregate-drift products | 0 |
| Exact/normalized QR duplicate groups | 0 / 0 |
| Invalid physical states | 0 |
| AVAILABLE NEW / USED | 126 / 810 |
| USED missing exact price | 28 |
| USED missing condition | 10 |
| Missing snapshot physical IDs | 8 |
| SOLD without non-cancelled snapshot | 63 |

## 4. Exact Phase D Alias Policy from Source

The pending implementation is deterministic:

- `DiscoQrCopyRepository.findByIdDiscoOrderByCopyNumber` returns rows by ascending unique `copy_number`.
- `synchronizeParentQrAlias` selects the first AVAILABLE row from that order.
- If no AVAILABLE row exists, it selects the first retained row in the same order.
- If no physical row exists, the dedicated alias synchronizer returns without changing the legacy parent alias.
- `refreshAggregateFields` and the general synchronization path use the same AVAILABLE-first, lowest-copy-number rule; the general synchronization path sets null only when it owns a synchronization that leaves no rows.

The repair used `AVAILABLE first, copy_number ASC, id ASC`. The `id` tie-break is defensive; production enforces unique `(id_disco, copy_number)`.

## 5. Alias Target Analysis

All products were active NEW products. Full QR strings were never emitted.

| Product | Physical rows (`id:state`) | AVAILABLE IDs | Alias before | Canonical copy | Repair |
|---:|---|---|---|---:|---|
| 529 | 4731:A, 4732:S | 4731 | SOLD 4732 | 4731 | Yes |
| 530 | 4733:S, 4734:A | 4734 | SOLD 4733 | 4734 | Yes |
| 542 | 4757:S, 4758:A | 4758 | SOLD 4757 | 4758 | Yes |
| 546 | 6264:S, 28355:A | 28355 | SOLD 6264 | 28355 | Yes |
| 616 | 6280:S, 6281:S, 6282:A, 6283:A | 6282, 6283 | SOLD 6281 | 6282 | Yes |
| 1437 | 16834:S, 16835:A, 16836:S | 16835 | SOLD 16836 | 16835 | Yes |
| 1438 | 16837:S, 16838:A | 16838 | SOLD 16837 | 16838 | Yes |
| 1945 | 28435:S, 28436:S, 28437:A, 28438:A | 28437, 28438 | SOLD 28435 | 28437 | Yes |
| 1952 | 28454:S, 28455:S, 28456:A, 28457:S, 28496:A | 28456, 28496 | noncanonical AVAILABLE 28496 | 28456 | Yes |

`A` means AVAILABLE and `S` means SOLD. Product 1952 required repair because Phase D chooses the lowest-numbered AVAILABLE copy, copy 28456, not the newer valid AVAILABLE alias copy 28496.

## 6. Alias Repair

The transaction locked the nine parent rows and all physical rows defining their canonical choice. It then required:

- all nine live parent/copy sets to match the plan fingerprints;
- every canonical copy ID and fingerprint to match the plan;
- exact AVAILABLE counts and persisted quantities to match;
- aggregate drift, QR duplicates, and invalid states to remain zero;
- all aliases to be wholly pre-repair or wholly canonical, never a mixed partial state.

The single `UPDATE` assigned each parent `disco.codigo_qr` from its canonical existing physical row. Result: 9 parent rows changed. Before commit, the same physical-set fingerprints, quantities, states, canonical IDs, aggregate invariant, and QR uniqueness were rechecked.

## 7. Alias Idempotency

The identical guarded repair was executed again after commit. It returned `ALREADY_APPLIED_ZERO_WRITES` with 0 rows updated. The after image reports 9/9 canonical targets and 0 remaining repair requirements.

## 8. Missing Snapshot Analysis

All eight completed sale details and their financial fields still exist. All seven products are NEW, soft-deleted, and excluded from active Catalog/Stock queries. Every exact snapshot token survives, but its physical row does not; none of the seven products has any remaining physical row.

| Missing copy | Product | Sale/detail | Sale | Deleted at | Physical now | Classification |
|---:|---:|---|---|---|---:|---|
| 4775 | 551 | 29 / 146 | COMPLETADA | 2026-08-12 02:31:20 | 0 | S1 |
| 4776 | 551 | 31 / 159 | COMPLETADA | 2026-08-12 02:31:20 | 0 | S1 |
| 4755 | 541 | 34 / 162 | COMPLETADA | 2026-08-12 02:27:47 | 0 | S1 |
| 4712 | 520 | 41 / 169 | COMPLETADA | 2026-08-12 01:57:32 | 0 | S1 |
| 4800 | 563 | 51 / 192 | COMPLETADA | 2026-08-12 02:30:58 | 0 | S1 |
| 6271 | 628 | 51 / 193 | COMPLETADA | 2026-08-12 01:50:08 | 0 | S1 |
| 6284 | 636 | 64 / 215 | COMPLETADA | 2026-08-12 01:48:06 | 0 | S1 |
| 6266 | 566 | 65 / 216 | COMPLETADA | 2026-08-12 02:30:52 | 0 | S1 |

Historical-existence evidence is direct: each persisted sale snapshot names the exact missing ID and retains the product, sale, quantity, unit price, and acquisition-cost snapshot. The most likely deletion path is the old replayable migration 011 cleanup: commit `fa974b1` added an explicit deletion of all copy rows belonging to tombstoned products on 2026-08-17, after these products were soft-deleted on 2026-08-12 and before checksum-ledger migration tracking was installed. This is a source-and-timestamp-supported inference, not an invented row-level audit event.

The deployed code loads every snapshot ID under lock and throws when any is missing, before the sale is marked cancelled. Pending Phase D does the same and adds ownership validation plus the modeled-inventory precheck. Both fail closed; neither creates a replacement copy or changes financial state.

## 9. Snapshot S1/S2/S3 Classification

| Class | Cases | Meaning |
|---|---:|---|
| S1 | 8 | Historical identity loss, safely contained |
| S2 | 0 | Current operational risk |
| S3 | 0 | Insufficient evidence |

The cases do not corrupt current inventory: the products are soft-deleted, have zero physical rows, and are excluded from Stock. Their financial history remains intact. They are intentionally non-cancellable through the normal endpoint unless a separately authorized historical procedure is designed.

## 10. SOLD Provenance Analysis

The exact 63-copy ID set stayed unchanged through the final check (`MD5` set fingerprint `16e626e7308537443277d0b3341f81b1`). Classification was conservative:

- V1 requires a SOLD Discogs import row for the same product and copy creation inside that import job's start/update window. Product-level SOLD evidence outside the copy's creation window was rejected.
- V2 requires an exact historical snapshot naming that copy; the three V2 references belong to cancelled sales.
- V3 requires sufficient migration/lineage proof for the SOLD state itself. No row met that standard. Manual batch membership alone proves receipt provenance, not why the row is SOLD.
- V4 contains every remaining row, including two products with later product-level SOLD import evidence and three manual-batch copies whose SOLD transition is not proven.

No sale relationship was manufactured from same-product correlations.

## 11. V1/V2/V3/V4 Classification

| Class | Copies | Products in class | NEW | USED | AVAILABLE | Stock contribution | Exact QR sale |
|---|---:|---:|---:|---:|---:|---:|---|
| V1 legitimate SOLD import | 30 | 30 | 0 | 30 | 0 | 0 | Rejected |
| V2 exact historical application sale | 3 | 3 | 2 | 1 | 0 | 0 | Rejected |
| V3 proven legacy/migration SOLD | 0 | 0 | 0 | 0 | 0 | 0 | N/A |
| V4 unexplained SOLD | 30 | 29 | 4 | 26 | 0 | 0 | Rejected |

V1 copy IDs: 15065, 15068, 15109, 15111, 15113, 15114, 15115, 15119, 15132, 15141, 15148, 15151, 15154, 15178, 15184, 15185, 15186, 15187, 20979, 21000, 21002, 21053, 21060, 21062, 21072, 21084, 21085, 21086, 21087, 21090.

V2 copy IDs: 6240, 6279, 20983.

V4 copy IDs: 12192, 12194, 12351, 12367, 14164, 14183, 14200, 14576, 15011, 15051, 15062, 15066, 15108, 15133, 15140, 15166, 15172, 15188, 15215, 15221, 17649, 17743, 17759, 17826, 17861, 24567, 24580, 24599, 28334, 28336.

The 30 V4 rows create provenance uncertainty, not a current inventory correctness failure: they are all SOLD, excluded from persisted AVAILABLE quantity and Stock valuation, and rejected by exact-copy sale because reservation requires `DISPONIBLE` under lock.

## 12. Current Inventory Impact

Final production remains coherent: 936 AVAILABLE + 132 SOLD = 1,068 physical rows, with 0 REMOVED, 0 invalid states, and 0 aggregate-drift products. The alias repair changed compatibility pointers only. The missing identities concern deleted products; the 63 rows remain non-available.

## 13. Stock Valuation Impact

Final read-only Phase E-equivalent totals:

| Metric | Result |
|---|---:|
| Imported NEW EUR | 1,960.640000 |
| Imported NEW UYU | 0 |
| Projected NEW UYU | 204,953.144250 |
| Projected USED known UYU | 807,129.300000 |
| AVAILABLE NEW / USED | 126 / 810 |
| USED excluded for missing price | 28 |

Only AVAILABLE physical rows are loaded. SOLD/REMOVED rows and deleted/no-row products contribute zero. The 28 missing USED prices are excluded and disclosed; the 10 missing conditions do not participate in arithmetic.

## 14. Exact QR Sale Safety

Physical QR identity remains authoritative. Exact reservation loads the requested ID/QR under lock and rejects null, missing, foreign-product, SOLD, or REMOVED rows. Production has 0 null/blank physical QRs and 0 exact/normalized duplicate groups. After repair, every authorized parent compatibility alias points to its deterministic canonical AVAILABLE row; no physical QR changed.

## 15. Deployment Revision Diff

Production and `origin/main` both point to `977a9dd7594a74b905639ce9a2e72725e3567999`. There is no later commit containing Phase A-E.

The pending workspace relative to that revision contains 30 modified tracked runtime/test files plus four untracked runtime/test files. The two untracked runtime files are:

- `sonograma-backend/src/main/java/com/sonograma/dto/StockValuationDTO.java`;
- `sonograma-backend/src/main/java/com/sonograma/service/StockValuationService.java`.

Runtime changes span four frontend files and eleven backend files; tests span five frontend and fourteen backend files, including the new `StockValuationServiceTest` and `FinalInventoryValuationIntegrationTest`. There is no migration, deployment-script, Compose, or production-properties diff. The runtime behavior implements Catalog presentation, exact USED commercial/lifecycle safety, Libro refresh behavior, and Stock valuation described by Phases A-E.

Because the deployment script fetches and checks out a branch, a dirty local workspace is not a deployable revision. Exact intended deployment commit: **none exists**.

Fresh verification of the workspace passed:

- backend: 524 tests, 0 failures, 0 errors, 1 skipped;
- frontend: 204 tests across 21 files, all passed;
- frontend ESLint: passed;
- frontend production build: passed;
- backend package with tests skipped: passed;
- `git diff --check`: passed before report generation.

## 16. Flyway / Startup Compatibility

Sonograma does not use Flyway; it uses `deploy/deploy.sh` plus `sonograma_schema_migrations` filename/checksum tracking. Production has 56 ledger rows through `053_manual_discogs_source_reconciliation.sql`. All 56 local migration checksums match, and pending migrations are 0.

Production runs profile `prod` with Hibernate `ddl-auto=validate`. `DataInitializer` is excluded by `@Profile("!prod")`. The two application runners that can mutate data are property-gated; both enabling environment variables are unset. No startup listener scans or reconciles physical-copy rows.

The remaining data is schema/runtime compatible:

- `DiscoQrCopy.precioVenta` and `condicionFisica` are nullable;
- Stock explicitly excludes/discloses null/non-positive USED prices;
- sale presentation tolerates legacy null commercial fields;
- `copy_ids_snapshot` is nullable text, not a foreign key;
- missing historical IDs cause cancellation conflict, not startup failure;
- unreferenced SOLD rows are valid lifecycle rows;
- active zero-stock products without physical rows remain supported and contribute zero.

## 17. Ten Deployment Blocker Criteria

| # | Criterion | Result | Evidence |
|---:|---|---|---|
| 1 | AVAILABLE physical quantity incorrect | PASS | Aggregate drift 0 |
| 2 | SOLD/REMOVED exact copy can be sold | PASS | Reservation requires locked AVAILABLE row |
| 3 | Valid AVAILABLE exact QR cannot be safely identified | PASS | No blank/duplicate QR; canonical aliases repaired |
| 4 | Stock valuation includes SOLD/REMOVED | PASS | Repository query loads AVAILABLE only |
| 5 | Modern USED sale can occur without exact identity | PASS | Sole USED auto-resolves exact row; multiple/manual require exact selection |
| 6 | Cancellation can create aggregate-only modeled stock | PASS | Modeled inventory requires/restores exact snapshot IDs; missing IDs fail before mutation |
| 7 | QR duplication/identity collision exists | PASS | Exact and normalized duplicates 0 |
| 8 | Deployment transforms valid production data incorrectly | PASS for audited workspace behavior | No pending migration/startup repair; however no deployable commit exists |
| 9 | Application startup/migration would fail | PASS for audited workspace/schema | Builds pass; 56/56 checksums match; pending migrations 0 |
| 10 | Current data violates a required normal-operation invariant | PASS | Remaining null/history cases are explicitly supported |

All ten data/code compatibility criteria pass for the audited workspace. The separate release-identity gate fails: there is no immutable intended revision. This prevents a controlled deployment despite the blocker matrix passing.

## 18. Final Fresh Production Check

Final target audit: `2026-10-08T18:19:26Z`. Full diagnostic: `2026-10-08T18:19:29Z`.

- aggregate drift: 0;
- exact/normalized QR duplicate groups: 0/0;
- invalid states: 0;
- authorized aliases canonical: 9/9;
- global stale parent aliases: 0;
- missing identities unchanged: 8/8, all S1;
- SOLD target count/fingerprint unchanged: 63 / `16e626e7308537443277d0b3341f81b1`;
- USED missing price/condition: 28/10, supported by pending code.

No unrelated concurrent production change occurred between the G3.1 baseline and final check.

## 19. Production Writes

Authorized G3.1 production writes:

- `disco.codigo_qr`: 9 rows updated;
- every other `disco` column: 0;
- `disco_qr_copy` rows/QR/state/number/price/condition: 0;
- quantities, product states, sales, snapshots, debts, pre-sales, imports: 0;
- second repair execution: 0 writes;
- deployments, migrations, container restarts: 0.

## 20. Remaining Known Historical Risks

- Eight completed sales cannot be cancelled normally because their exact historical rows were deleted. They fail safely and need a separately designed historical policy if cancellation is ever required.
- Thirty SOLD rows remain V4. Their provenance is unknown, but their current containment is sound.
- Twenty-eight AVAILABLE USED copies lack exact price and ten lack condition; 29 copies need at least one manual commercial decision.
- The working tree is large and dirty. The intended production content must be curated into an immutable reviewed commit without accidentally including unrelated reports or local production artifacts.

## 21. Deployment Recommendation

Production data is ready for the pending hardening behavior, but the controlled Lightsail deployment is not ready to execute because no exact intended revision exists. Create and review a release commit containing the approved runtime/tests/reports, confirm its hash and clean checkout, then rerun the final diff/build and compact read-only production gate against that hash. Do not deploy the current dirty workspace and do not deploy `977a9dd…` expecting Phase A-E, because that commit is already production and does not contain them.

Explicit answers:

- Parent aliases requiring repair: **9**.
- Parent aliases repaired: **9**.
- Product 1952 required repair under actual Phase D code: **yes, to copy 28456**.
- Physical QR changes: **0**.
- Authorized aliases canonical afterward: **yes, 9/9**.
- Missing snapshots: **S1=8, S2=0, S3=0**.
- Missing snapshot cases currently corrupt inventory: **no**.
- SOLD distribution: **V1=30, V2=3, V3=0, V4=30**.
- SOLD rows unexplained: **30**.
- Those SOLD rows sellable by exact QR: **no**.
- SOLD rows included in Stock valuation: **no**.
- Twenty-eight missing USED prices block deployment: **no**.
- Ten missing USED conditions block deployment: **no**.
- Exact revision to deploy: **none exists yet**.
- Pending migrations: **0**.
- Startup mutates production inventory: **no under current production configuration**.
- Every blocker criterion passed: **yes for data/code compatibility; the separate immutable-revision gate did not**.
- Ready for controlled Lightsail deployment: **no, not until an exact release commit exists and is revalidated**.

PRE-DEPLOY STATUS: BLOCKED
