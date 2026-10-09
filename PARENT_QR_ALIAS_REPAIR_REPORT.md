# Parent QR Alias Repair Report

Repair date: 2026-10-08
Scope: Phase G3.1 authorized products 529, 530, 542, 546, 616, 1437, 1438, 1945, and 1952

## Result

The guarded production repair committed successfully. Nine parent compatibility aliases were changed to the deterministic Phase D canonical AVAILABLE physical copy. A second execution wrote zero rows.

| Metric | Result |
|---|---:|
| Authorized targets | 9 |
| Required repair immediately before write | 9 |
| Parent `disco.codigo_qr` rows updated | 9 |
| Physical-copy rows updated | 0 |
| Physical QR/state/number changes | 0 / 0 / 0 |
| Quantity/product-state changes | 0 / 0 |
| Targets canonical after commit | 9 |
| Second-run writes | 0 |

## Canonical Rule

The pending Phase D repository returns copies by ascending `copy_number`. The alias synchronizer selects the first AVAILABLE row, otherwise the first retained row, and leaves a no-row legacy alias unchanged. Production enforces unique `(id_disco, copy_number)`, so the choice is deterministic. The repair used the same AVAILABLE-first, ascending-copy-number order with `id` as a defensive tie-break.

Product 1952 had parent alias copy 28496, which was AVAILABLE but noncanonical. Copies 28456 and 28496 were both AVAILABLE; copy 28456 has the lower copy number and was therefore the Phase D target.

## Transaction Guards

One serializable transaction:

1. locked the exact nine parent rows;
2. locked all physical rows for those products against concurrent mutation;
3. reloaded copy state, number, QR fingerprint, and canonical selection;
4. matched every parent/copy set against the approved plan fingerprints;
5. required persisted quantity to equal AVAILABLE count;
6. required global aggregate drift, QR duplicates, and invalid states to remain zero;
7. updated only `disco.codigo_qr` from the selected existing physical row;
8. repeated every target/invariant check before commit.

The transaction would roll back on target drift or mixed partial application. Unrelated production activity was not used as a global-count freeze.

## Target Result

| Product | Alias before | Canonical after |
|---:|---|---:|
| 529 | SOLD copy 4732 | AVAILABLE copy 4731 |
| 530 | SOLD copy 4733 | AVAILABLE copy 4734 |
| 542 | SOLD copy 4757 | AVAILABLE copy 4758 |
| 546 | SOLD copy 6264 | AVAILABLE copy 28355 |
| 616 | SOLD copy 6281 | AVAILABLE copy 6282 |
| 1437 | SOLD copy 16836 | AVAILABLE copy 16835 |
| 1438 | SOLD copy 16837 | AVAILABLE copy 16838 |
| 1945 | SOLD copy 28435 | AVAILABLE copy 28437 |
| 1952 | noncanonical AVAILABLE copy 28496 | AVAILABLE copy 28456 |

No full QR appears in any artifact; only non-reversible MD5 operational fingerprints are stored.

## Artifacts and Checksums

- `parent-qr-alias-repair-plan.json`: `7bbe975f58806e5f76c68d81f30e9e16c99e27a99dd8cb52b00519fc37367bb1`
- `parent-qr-alias-before.json`: `60d17d17e53cfbdc724ef413dde9b746e8e6867068a6b321f090e8283f1bc690`
- `parent-qr-alias-after.json`: `1fe7d63f5029de2b55d8a4e07005c22a5bb92e419ba3864e78bc2caf520aa5c5`
- `parent-qr-alias-repair-execution.json`: `df666b4f4872a341c4fe1feea37887f9c48fede6107f61e9326e1964010c7948`
- `parent-qr-alias-repair-idempotency.json`: `2cb226bbfaf0ab5354968c871b0c5c63fefd1d7b13b7059fea40bc18c89110a3`

## Final Verification

The final target audit reports all nine parent aliases resolving to the planned canonical copy IDs with unchanged physical-set fingerprints. The full diagnostic reports zero stale parent aliases globally, zero aggregate drift, zero invalid states, and zero exact or normalized QR duplicates.

PARENT QR ALIAS REPAIR STATUS: SUCCESS
