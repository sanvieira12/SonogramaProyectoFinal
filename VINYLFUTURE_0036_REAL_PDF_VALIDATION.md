# Vinyl Future 0036 Real PDF Validation

## Invoice detected

- Source file: `/Users/admin/Downloads/0036-188471.pdf`
- Invoice number detected: `0036-188471`
- Invoice date detected: `2026-08-31`
- PDF pages processed: 2
- Official declared Quantity detected: 32 physical copies

The file was passed through the current `PdfInvoiceParser.parseInvoiceWithDiagnostics` implementation and the current Vinyl Future `/importar/vinylfuture/validar` validation path without changing application code.

## Validation result

| Metric | Current implementation result |
|---|---:|
| Declared Quantity | 32 |
| Source product rows detected | 21 |
| Successfully parsed rows | 20 |
| Successfully parsed physical quantity | 28 |
| Rows marked `REVIEW_REQUIRED` | 1 |
| Validation pending physical quantity | 4 |
| Reconciliation difference (`declared - parsed`) | +4 copies short |
| `consistent` | `false` |
| `requiresReview` | `true` |

Independent visual inspection of both rendered pages shows 23 actual source product rows and 32 physical copies. The parser detects 21 of those rows, parses 20, marks one for review, and completely misses two additional rows.

## Source rows detected

| Source row | Page | Catalogue code | Artist / title | Parsed quantity | Parse status |
|---:|---:|---|---|---:|---|
| 1 | 1 | COMMUNIQUE011 | Invisible - The Next EP | 3 | `PARSED` |
| 2 | 1 | DHS999 | Dimensional Holofonic Sound aka Dhs - Holofonic Cuts (reissue) | 2 | `PARSED` |
| 3 | 1 | GM-05 | Gerald Mitchell - Groove Within The Groove | 2 | `PARSED` |
| 4 | 1 | KOMEX138 | Raxon - Speicher 138 | 1 | `PARSED` |
| 5 | 1 | LITA22611 | CHARANJIT SINGH - SYNTHESIZING: TEN RAGAS TO A DISCO BEAT LP 2x12" | Not parsed; estimated 2 | `REVIEW_REQUIRED` |
| 6 | 1 | MAO-V001 | Barac - Look at the cross LP | 1 | `PARSED` |
| 7 | 1 | MXLP4300 | Various - PACHA IBIZA CLASSICS LP 3x12" | 1 | `PARSED` |
| 8 | 1 | OYSTER80 | Michelle - Unfailing Love | 1 | `PARSED` |
| 9 | 1 | OYSTER80 | Michelle - Unfailing Love | 2 | `PARSED` |
| 10 | 1 | OYSTER80 | Michelle - Unfailing Love | 1 | `PARSED` |
| 11 | 1 | PLZ044 | Vinyl Speed Adjust - The Chosen Path EP | 1 | `PARSED` |
| 12 | 1 | RCM101120LP | John Frusciante - The Empyrean | 1 | `PARSED` |
| 13 | 1 | RCM101120LP | John Frusciante - The Empyrean | 1 | `PARSED` |
| 14 | 1 | RCM101120LP | John Frusciante - The Empyrean | 1 | `PARSED` |
| 15 | 1 | RRR018 | The Nighttripper - Sinister World | 1 | `PARSED` |
| 16 | 1 | RWX025 | Vinyl Speed Adjust - All About Us EP | 2 | `PARSED` |
| 17 | 1 | SUSH69 | Housey Doingz - A Sillybration I | 1 | `PARSED` |
| 18 | 2 | TOKO5 | ATTABOY - IN TOO DEEP | 1 | `PARSED` |
| 19 | 2 | TOKO6 | KLARKY CAT - GUMBO | 1 | `PARSED` |
| 20 | 2 | TOKO6 | KLARKY CAT - GUMBO | 1 | `PARSED` |
| 21 | 2 | TRANS1006 | Señor Coconut - El Baile Alemán | 3 | `PARSED` |

## Consolidated quantities

These are the consolidations returned by the current validation logic. They sum to 28 parsed physical copies.

| Catalogue code | Artist / title | Source rows | Row quantities | Consolidated quantity |
|---|---|---|---|---:|
| COMMUNIQUE011 | Invisible - The Next EP | 1 | 3 | 3 |
| DHS999 | Dimensional Holofonic Sound aka Dhs - Holofonic Cuts (reissue) | 2 | 2 | 2 |
| GM-05 | Gerald Mitchell - Groove Within The Groove | 3 | 2 | 2 |
| KOMEX138 | Raxon - Speicher 138 | 4 | 1 | 1 |
| MAO-V001 | Barac - Look at the cross LP | 6 | 1 | 1 |
| MXLP4300 | Various - PACHA IBIZA CLASSICS LP 3x12" | 7 | 1 | 1 |
| OYSTER80 | Michelle - Unfailing Love | 8, 9, 10 | 1, 2, 1 | **4** |
| PLZ044 | Vinyl Speed Adjust - The Chosen Path EP | 11 | 1 | 1 |
| RCM101120LP | John Frusciante - The Empyrean | 12, 13, 14 | 1, 1, 1 | **3** |
| RRR018 | The Nighttripper - Sinister World | 15 | 1 | 1 |
| RWX025 | Vinyl Speed Adjust - All About Us EP | 16 | 2 | 2 |
| SUSH69 | Housey Doingz - A Sillybration I | 17 | 1 | 1 |
| TOKO5 | ATTABOY - IN TOO DEEP | 18 | 1 | 1 |
| TOKO6 | KLARKY CAT - GUMBO | 19, 20 | 1, 1 | **2** |
| TRANS1006 | Señor Coconut - El Baile Alemán | 21 | 3 | 3 |

The three known expected consolidations are correctly preserved: `OYSTER80 = 4`, `RCM101120LP = 3`, and `TOKO6 = 2`.

## Rows requiring review

The current parser marks exactly one row for review:

| Page | Original PDFBox-extracted text | Estimated quantity | Current reason |
|---:|---|---:|---|
| 1 | `LITA22611 - CHARANJIT SINGH- SYNTHESIZING: TEN RAGAS TO A DISCO BEAT LP 2x12"48,79 2 97,58` | 2 | `La estructura de la línea no coincide con el formato esperado de Vinyl Future.` |

The visible row is valid, but PDF extraction removes the space between the format and unit price (`2x12"48,79`). Both current item regular expressions require whitespace before the unit price, so the row is detected as product-like but not parsed.

Two more source rows fail parsing but are not marked for review because the current candidate detector requires at least two valid money tokens:

| Page | Catalogue code | Original PDFBox-extracted text | Visible quantity | Why it is absent from review |
|---:|---|---|---:|---|
| 1 | K7046XXXLP | `K7046XXXLP - Kruder & Dorfmeister- DJ-Kicks Kruder & Dorfmeister (30th Anniversary Box) (3x5102,9")9 1 50,99` | 1 | The long description overlaps the unit-price column. PDFBox interleaves the printed `50,99` into the ending `3x12"`, leaving only the final `50,99` as a valid money token. `looksLikeProductRow` therefore returns false. |
| 1 | MMV004 | `MMV004 - Manuel De Lorenzi, Giacomo Silvestri, Rush Arp, Barac, JNJS- Manuel De Lorenzi &1 F0r,i4e9nds Ep 1 10,49` | 1 | The long description overlaps the unit-price column. PDFBox interleaves the printed `10,49` into `Friends`, again leaving only the final `10,49` as a valid money token. `looksLikeProductRow` therefore returns false. |

These three rows account for all four missing copies: `2 + 1 + 1 = 4`.

## Quantity reconciliation

The current result is:

```text
declaredQuantity = 32
parsedPhysicalQuantity = 28
pendingPhysicalQuantity = 4
difference = declaredQuantity - parsedPhysicalQuantity = 4
```

Therefore `declaredQuantity == parsedPhysicalQuantity` is false: the current implementation produces `32 != 28`, not the expected `32 == 32`.

The physical quantities visible in the invoice reconcile independently as follows:

```text
28 currently parsed
+ 2 LITA22611
+ 1 K7046XXXLP
+ 1 MMV004
= 32 physical copies
```

## Parser issues found

1. The item regex assumes whitespace exists immediately before the unit price. LITA22611 violates this after PDF extraction even though the rendered invoice has distinct visual columns.
2. Long descriptions can enter the unit-price column. `PDFTextStripper` with position sorting then interleaves price glyphs into the description for K7046XXXLP and MMV004.
3. The product-candidate heuristic requires two recognizable money tokens. When the unit price is interleaved, otherwise recognizable rows ending in a valid quantity and line total disappear instead of becoming `REVIEW_REQUIRED` rows.
4. PDFBox emits a non-fatal warning while reading an embedded font table. It still reads both pages and the declared summary correctly; the reconciliation failure is caused by row/column extraction assumptions, not by an aborted PDF load.

## Recommended minimal fix

No fix was applied.

The smallest safe adjustment is a localized fallback for lines that fail the existing strict/loose regexes:

1. Preserve the current regex path for already working rows.
2. Recognize a candidate when it has the catalogue/artist separators and ends with a valid `quantity + line total`, even when only one money token survives. This ensures K7046XXXLP and MMV004 cannot disappear silently.
3. For those failed candidates, recover the four invoice columns by PDF text position (`Description`, `Unit Price`, `Quantity`, `Sum`) for that row rather than by flattened character order. This safely separates overlapping description and price glyphs and also handles LITA22611's missing whitespace.
4. Keep price-times-quantity validation and return `REVIEW_REQUIRED` if positional recovery remains ambiguous.

Merely changing the whitespace quantifier in the current regex would recover LITA22611, but it would not safely recover K7046XXXLP or MMV004. Inferring unit price from line total could reconcile quantity, but would retain corrupted titles and is therefore not the safe recommendation.

## Confirmation that validation was read-only

The validation run was read-only with respect to application data:

- The real PDF was submitted only to the current validation endpoint path. The confirm/import endpoints were not called.
- The validation collaborators were instrumented. The only repository interaction was `existsByNumeroFacturaCompra("0036-188471")`, a read-only duplicate check.
- There were no calls to catalogue save/update, stock mutation, `PedidoService`, QR synchronization, shipping-order creation, audio/media persistence, import-batch storage, CSV/ZIP generation, Vinyl Future search, Vinyl Future scraping, or asset download/storage.
- No Discogs path was invoked.
- The validation result was held only in the controller's in-memory validation-session map.
- No application source file, migration, scraping logic, ZIP logic, catalogue identity logic, or stock logic was changed. This Markdown report is the only requested repository artifact created by this validation work.
