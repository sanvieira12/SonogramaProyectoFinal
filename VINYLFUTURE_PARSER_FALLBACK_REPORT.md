# Vinyl Future Parser Fallback Report

## Root cause confirmed

PDFBox's position-sorted flattened text can merge or interleave the end of a long description with the Unit Price glyphs. In the real two-page invoice, the product rows remain visually present and their Unit Price, Quantity, and Sum values remain in separate table columns, but the flattened text is not always parseable by the existing strict/loose regexes.

The previous candidate heuristic also required at least two recognizable money tokens. When the Unit Price token was damaged by interleaving, a probable product row with a valid right-side quantity and line total could disappear instead of being retained for review.

## Files modified

Files changed specifically for this parser correction:

- `sonograma-backend/src/main/java/com/sonograma/service/PdfInvoiceParser.java`
- `sonograma-backend/src/test/java/com/sonograma/service/PdfInvoiceParserTest.java`
- `VINYLFUTURE_PARSER_FALLBACK_REPORT.md`

No DTO, database migration, controller, frontend, search, scraper, media, ZIP, catalogue identity, QR, stock, or business-rule change was required for this correction. The working tree already contained separate Phase 1 changes before this task; they were preserved.

## Candidate detection changes

The original two-money-token condition remains useful but is no longer the only way to retain a probable row. A failed flattened line is now considered a product candidate when it has:

- a bounded catalogue-code-like prefix followed by Vinyl Future's ` - ` product separator;
- an additional artist/title separator in the description;
- a valid integer quantity and money-formatted line total at the right side of the flattened row; or
- the prior evidence of at least two money tokens.

Reserved headers, invoice metadata, postage, fees, totals, signatures, and summary rows remain excluded. A failed candidate is emitted as `REVIEW_REQUIRED` with a Spanish reason rather than silently dropped.

## Positional fallback implementation

The strict and loose flattened-text regexes remain the primary parser path. The coordinate-based logic runs only after both regexes fail.

For each page, the fallback captures PDFBox `TextPosition` data and derives the table boundaries from the visible `Description`, `Unit Price`, `Quantity`, and `Sum` header positions. It then matches the failed flattened row to the corresponding positional row by its generic catalogue-code prefix, preserving repeated rows in page order.

The fallback separates the original content-stream segments into description, unit price, quantity, and line total. This supports:

- a price immediately adjacent to a format without whitespace;
- a long description that visually reaches or overlaps the Unit Price column;
- a price run drawn over description glyphs and therefore interleaved by position-sorted flattening;
- multiple pages with repeated product-table headers.

The description is reconstructed from its own source segment before code, artist, title, and existing format extraction are applied. Price glyphs are not retained in the product title.

## Arithmetic validation

Every positional recovery must produce exactly one Unit Price, one positive Quantity, and one Sum value. The fallback validates `unitPrice * quantity` against the printed line total using the existing EUR-cent tolerance of `0.02`.

An arithmetically consistent recovery is parsed. A missing, duplicate, structurally ambiguous, or arithmetically inconsistent recovery remains `REVIEW_REQUIRED`; no quantity, price, code, or description is fabricated.

## Real PDF 0036 result

The real file `/Users/admin/Downloads/0036-188471.pdf` was parsed by the regression test. The test can also be pointed at another copy with the `vinylfuture.real-pdf` system property.

- Declared Quantity: 32
- Parsed Physical Quantity: 32
- Pending Quantity: 0
- Product source rows: 23
- Parsed source rows: 23
- Review-required source rows: 0
- Consistent: true

Known consolidations remain unchanged:

- `OYSTER80`: 4
- `RCM101120LP`: 3
- `TOKO6`: 2

## Previously problematic rows

- `LITA22611`: quantity 2; Unit Price 48.79; Sum 97.58; format `2x12"`; parsed with a clean title.
- `K7046XXXLP`: quantity 1; Unit Price 50.99; Sum 50.99; the complete title `DJ-Kicks Kruder & Dorfmeister (30th Anniversary Box) (3x12")` is reconstructed without price glyphs.
- `MMV004`: quantity 1; Unit Price 10.49; Sum 10.49; the title `Manuel De Lorenzi & Friends Ep` is reconstructed without price glyphs.

These identities occur only in regression assertions and this report, never in production parser logic.

## Regression tests

`PdfInvoiceParserTest` now includes focused coverage for:

- missing whitespace between format and Unit Price;
- long descriptions overlapping the Unit Price column;
- a candidate with only the flattened line total recognizable as money;
- successful positional recovery with arithmetic validation;
- inconsistent positional recovery remaining `REVIEW_REQUIRED`;
- simple rows continuing through the original regex behavior;
- the real 0036 PDF reconciling 32 physical copies;
- clean reconstructed descriptions for overlapping rows;
- unchanged OYSTER80, RCM101120LP, and TOKO6 consolidations.

## Full test results

- Focused parser tests: 17 passed, 0 failed, 0 skipped.
- Vinyl Future controller/import tests (`ImportControllerTest`): 6 passed, 0 failed, 0 skipped.
- Complete backend suite: 254 tests reported; 252 passed, 1 failed, 1 skipped.
- The one full-suite failure reproduces in isolation in `EstadisticasServiceTest.serieMensualConservaCentavosYCoincideConElLibroEnLosIdsYMontosIncluidos`: expected 36955, obtained 30000. This is unrelated statistics/Libro de Ventas behavior and was deliberately not changed because it is outside and protected from this parser task.
- Frontend tests were not run because this correction changed no frontend file.
- `git diff --check`: passed.

## Existing behavior preserved

- The current strict/loose regex parser is still the primary path.
- Existing simple rows and repeated rows retain their prior behavior.
- The fallback is local to PDF parsing failures and performs no search, scraping, enrichment, persistence, stock, QR, media, ZIP, or pricing work.
- No new migration or schema change was introduced.
- No English user-facing UI string was introduced; new diagnostic reasons are in Spanish.
- Phase 2 was not started, and manual Vinyl Future URL import was not implemented.
- No Libro de Ventas files were modified.
- No pricing or markup rules were modified.
- No sales, debt, revenue, statistics, dashboard, or financial-report logic was modified.
- Multi-disc pricing behavior remains unchanged.

## Remaining parser limitations

- Positional recovery requires a recognizable Vinyl Future product-table header on the page so column boundaries can be derived safely.
- The fallback expects the supplier's current one-visual-row-per-product layout and recognizable catalogue/artist/title separators.
- If PDF glyph coordinates, field multiplicity, or arithmetic do not identify one safe structure, the row remains `REVIEW_REQUIRED` by design.
- The declared invoice quantity is used only for reconciliation and is never used to invent missing rows or quantities.

## Confirmation of generalization

Production parsing contains no invoice-, catalogue-code-, title-, quantity-, or expected-result special case. In particular, there are no production-code checks for:

- `0036-188471`
- `LITA22611`
- `K7046XXXLP`
- `MMV004`

Column boundaries come from each page's table header, product matching uses generic supplier-layout structure, and validation uses the row's own printed monetary and quantity values. The implementation is therefore applicable to future Vinyl Future invoice numbers, product codes, descriptions, formats, prices, quantities, repeated rows, and page counts that retain the same supplier table layout characteristics.
