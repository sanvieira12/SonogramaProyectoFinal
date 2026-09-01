# Sonograma Import System Audit

Audit date: 2026-09-01

Scope: complete Vinyl Future and Discogs import paths in the backend, frontend, persistence model, SQL migration scripts, file storage, asynchronous processing, error handling, and tests.

Method: static execution-path analysis plus the existing automated test suites. No production code, schema, API, or frontend behavior was changed.

Confidence labels used throughout:

- **CONFIRMED**: the behavior follows directly from the current code or test result.
- **HIGHLY LIKELY**: the required code condition is present; whether it has occurred in production depends on timing or input.
- **POSSIBLE**: a plausible edge case exists, but production data or runtime traces would be needed to prove occurrence.

Finding counts used by this report: **4 CRITICAL, 12 HIGH, 12 MEDIUM, 4 LOW**.

## 1. Executive Summary

Sonograma does not have one import system. It has three Vinyl Future paths and two Discogs paths, with only partial reuse between them:

1. the primary asynchronous Vinyl Future PDF-to-catalogue flow in `ImportController`;
2. the older `Pedido` PDF -> enrichment -> catalogue flow in `PedidoService`;
3. a separate Vinyl Future Excel preview/confirm flow in `VinylFutureImportService`;
4. a synchronous single-URL Discogs flow in `DiscogsImportService`;
5. a persistent staged Discogs Excel/job flow in `DiscogsImportJobService`.

The domain model itself can represent one catalogue product with multiple physical copies: `Disco` is the catalogue product and its currently available quantity, while `DiscoQrCopy` represents physical copies. The largest reliability problem is that imports do not use one durable, database-enforced identity for the catalogue product or source operation. Discogs release IDs exist only in URLs/staging rows, Vinyl Future supplier codes are matched differently by different services, invoice uniqueness is application-only, and almost all existence checks are non-atomic `find -> save` sequences.

The observed symptom family is therefore a combination, not one bug:

- **Duplicate catalogue releases** are directly possible in single-URL Discogs imports and under concurrent check-then-insert races in every path.
- **Duplicate or missing physical copies** can result from paths that alternately add quantity, overwrite quantity, or skip a duplicate code entirely.
- **Missing invoice items** can start in the permissive PDF parser, which silently ignores lines it cannot match and does not stop the main import on summary-count disagreement.
- **Incomplete metadata** is expected after transient network/API failures because some flows deliberately persist without enrichment; Discogs bulk explicitly marks metadata failures as ready for catalogue import.
- **Partial state** is possible because the primary Vinyl Future order commits in `REQUIRES_NEW`, catalogue work commits separately, files live outside the database, and ZIP creation occurs after the database transaction.
- **Intermittent hangs** have a concrete concurrency explanation: both Vinyl Future executor designs submit child tasks to the same bounded pool whose worker is waiting for those children. Four concurrent primary imports, or three concurrent legacy enrichment runs, can starve their respective pools.

The existing test suite is useful but does not exercise the failure modes above. Frontend tests passed (84/84). Backend import-focused suites passed, including `ImportControllerTest` (3), `PdfInvoiceParserTest` (8), `DiscogsExcelParserTest` (11), `DiscogsImportJobServiceTest` (17), `DiscogsApiClientTest` (2), and related scraper/asset/ZIP tests. The full backend run executed 242 tests with one unrelated failure in `EstadisticasServiceTest` and one skipped test. There are no tests for concurrent same-source imports, executor starvation, process restart recovery, single-vs-bulk Discogs identity, wrapped PDF product lines, or transaction/file rollback.

## 2. Import Architecture

### 2.1 Application structure

- Frontend: React 19/Vite, with import screens in `frontend/src/pages/importar/VinylFutureTab.jsx` and `frontend/src/pages/importar/DiscogsTab.jsx`; HTTP calls are in `frontend/src/api/sonograma.js`.
- Backend: Spring Boot 3.2/Java 21, layered into controllers, services, repositories, DTOs, and JPA entities under `sonograma-backend/src/main/java/com/sonograma`.
- Database: PostgreSQL in production; H2 `create-drop` in the dev/test profile. Production uses Hibernate `ddl-auto=validate` (`application-prod.properties:28`).
- Schema management: SQL files live under `docs/migraciones`, but the Maven project has no Flyway dependency and the scripts are not in Flyway's runtime location. `docs/REGISTRO_ENTREGA_FINAL.md:19` explicitly says migrations are manually administered without an automatic version table. These are **manual migration scripts**, not an active Flyway pipeline.
- File storage: Vinyl Future media, Discogs covers/ZIPs, and original PDFs are stored on the local filesystem, while only paths/URLs are persisted in the database.
- Background work: hand-built `ExecutorService` pools in `ImportController`, `PedidoService`, and `DiscogsImportJobService`; no durable queue or scheduler.

### 2.2 Controllers and entry points

| Source | Endpoint | Controller | Main service/path |
|---|---|---|---|
| Vinyl Future PDF, primary | `POST /importar/vinylfuture-catalogo` | `ImportController.importarFacturaAlCatalogo()` (`ImportController.java:100`) | `processImport()` |
| Vinyl Future PDF, ZIP/CSV | `POST /importar/vinylfuture-csv` | `ImportController.procesarFactura()` (`ImportController.java:169`) | same `processImport()` |
| Vinyl Future PDF, order only | `POST /pedidos/upload-pdf` | `PedidoController.uploadPdf()` (`PedidoController.java:29`) | `PedidoService.crearDesdePdf()` |
| Vinyl Future PDF, control workbook | `POST /pedidos/upload-control` | `PedidoController.uploadControl()` (`PedidoController.java:42`) | `PedidoControlImportService` + `InvoiceControlWorkbookService` |
| Vinyl Future staged order enrichment/catalogue | `POST /pedidos/{id}/enriquecer`, `.../importar-catalogo` | `PedidoController` (`PedidoController.java:106,112`) | `PedidoService.lanzarEnriquecimiento()` / `importarAlCatalogo()` |
| Vinyl Future Excel | `POST /importaciones/vinylfuture/preview`, `/confirmar` | `ImportacionController` (`ImportacionController.java:45,60`) | `VinylFutureImportService` |
| Discogs single URL | `POST /importaciones/discogs/desde-link`, `/guardar` | `ImportacionController` (`ImportacionController.java:82,92`) | `DiscogsImportService` |
| Discogs Excel/bulk | `POST /importaciones/discogs/jobs`, jobs/retry/import/ZIP endpoints | `ImportacionController` (`ImportacionController.java:100-138`) | `DiscogsImportJobService` |

### 2.3 Core domain objects

- `Disco`: one catalogue product/release-like record. It owns descriptive metadata and `cantidadCopias`, which current services treat as *available* quantity (`Disco.java:14-121`).
- `DiscoQrCopy`: one physical copy, unique by QR and `(id_disco, copy_number)` (`DiscoQrCopy.java:9-39`).
- `Pedido`: supplier invoice/order header (`Pedido.java:13-144`).
- `PedidoItem`: original invoice line; optionally linked to a `Disco` (`PedidoItem.java:9-87`).
- `DiscogsImportJob` and `DiscogsImportRow`: durable staging/audit records for the Excel path only (`DiscogsImportJob.java`, `DiscogsImportRow.java`).
- `CatalogAudioPreview`: optional Vinyl Future MP3 or Discogs/YouTube preview metadata.
- `ShippingOrder`/`ShippingOrderItem`: an additional receiving/order projection created by the primary Vinyl Future path.

The intended cardinality is:

```text
source import operation
  -> source rows/order items
  -> one catalogue Disco per identified edition/product
  -> N DiscoQrCopy physical copies
```

Current code sometimes collapses or expands the wrong layer: repeated source rows may correctly become copies in Discogs bulk, may be skipped in Vinyl Future Excel, may overwrite stock in legacy `PedidoService`, or may create multiple `Disco` rows in Discogs single import.

## 3. Vinyl Future Pipeline

### 3.1 Primary UI path: asynchronous PDF to catalogue

```text
VinylFutureTab.PdfExport.procesar()
  -> api.importar.vinylfutureCatalogo(file)
  -> ImportController.importarFacturaAlCatalogo()
  -> in-memory VinylFutureJobState + fixed pool submission
  -> ImportController.runVinylFutureJob()
  -> outer TransactionTemplate
  -> ImportController.processImport()
     -> PdfInvoiceParser.parseInvoice()
     -> PedidoService.persistirVinylFuture() [REQUIRES_NEW]
     -> invoice-level catalogue duplicate check
     -> mergeExactRepeatedRows()
     -> VinylFutureSearchService.buscar() [parallel]
     -> VinylFutureScraperService.scrape() + strongMatch() [parallel]
     -> VinylFutureAssetService.storeAssetsWithResult() [parallel]
     -> findExistingDisco() / buildDisco() / mergeDisco()
     -> DiscoRepository.save()
     -> DiscoQrCopyService.synchronize()
     -> AudioPreviewService.guardarDesdeTracks()
     -> PreVentaCodeMatcher.linkPendingPreSales()
     -> ShippingOrderService.crearDesdeImport()
  -> transaction commits
  -> CsvExportService.buildCsv()
  -> ZipBundleService.buildZip()
  -> VinylFutureImportBatchService.store()
  -> frontend polls job and optionally downloads ZIP
```

Relevant locations: `VinylFutureTab.jsx:240-359`, `sonograma.js:302-339`, `ImportController.java:94-163,264-436`, `PedidoService.java:143-205`.

Important behavior:

- The PDF parser extracts all page text with PDFBox and treats each extracted text line as a candidate product (`PdfInvoiceParser.java:116-143`).
- Exact repeated invoice lines are merged by normalized code + artist + album + unit price, adding quantities (`ImportController.java:572-610`).
- Enrichment failure does **not** prevent catalogue persistence; the invoice fields are enough to build a basic `Disco`.
- Existing products are found first by exact, case-sensitive `codigoInterno`, then by case-insensitive exact artist/title plus permissive format/label matching (`ImportController.java:483-511`).
- A new item sets invoice number/date, cost, auto price, available quantity, and metadata. An existing item increments `cantidadCopias` (`ImportController.java:439-569`).
- QR synchronization materializes physical copies after quantity changes.
- The ZIP is an export side product, not the source of database persistence. Its layout uses generated folder/file names and `missing_media.txt`; there is no `info.json`, and the stored media files are not universally named `cover.jpg` inside the exported ZIP.

### 3.2 PDF -> `Pedido` and control Excel

`PedidoController.uploadPdf()` invokes `PedidoService.crearDesdePdf()` (`PedidoService.java:54-139`). It persists a `Pedido`, saves the original PDF to the filesystem, persists every parsed `PedidoItem`, and returns a boolean warning when the PDF summary quantity differs from the parsed item quantity. It does not reject the mismatch.

`PedidoController.uploadControl()` wraps order creation and workbook generation in `PedidoControlImportService.importAndGenerate()` (`PedidoControlImportService.java:17-29`). `InvoiceControlWorkbookService` fills an `.xlsx` template from the stored order (`InvoiceControlWorkbookService.java:75-174`) and does reject quantity/line-total mismatches during workbook validation. This validation is not used by the primary catalogue endpoint.

The workbook is an output/control artefact. It is not read back by the primary PDF flow.

### 3.3 Legacy staged `Pedido` enrichment and catalogue import

```text
Pedido upload
  -> Pedido + PedidoItem rows
  -> PedidoService.lanzarEnriquecimiento()
  -> PedidoEnrichmentService.procesarItem()
  -> user review
  -> PedidoService.importarAlCatalogo()
  -> upsertDisco() + QR + audio
```

This path uses a separate fixed executor (`PedidoService.java:47,311-349`) and a different upsert (`PedidoService.java:398-435`). It does not share the primary `ImportController.findExistingDisco()/mergeDisco()` policy. Critically, an existing `Disco` has its quantity set to the order-line quantity rather than incremented (`PedidoService.java:424`). A search/scrape miss is marked `ENRICHED`, not failed (`PedidoEnrichmentService.java:34-48`).

### 3.4 Vinyl Future Excel path

`VinylFutureImportService.parsearExcel()` reads only sheet 0 and assumes row 0 is the header (`VinylFutureImportService.java:89-105`). `confirmarImport()` creates `Disco`, QR copies, and audio directly (`VinylFutureImportService.java:113-152`). It has no web enrichment and no invoice/order association. Its only duplicate check is an exact, case-sensitive internal code; a matching code is skipped rather than adding copies (`VinylFutureImportService.java:119-124`).

The component implementing this path, `ExcelImport`, remains in `VinylFutureTab.jsx:67-238`, but the exported tab renders only `<PdfExport />` at `VinylFutureTab.jsx:449-454`. The backend endpoints are still callable.

### 3.5 Pricing and format decisions

The main persistence flows call `CatalogPricingService.calculate()`. The current default settings are EUR/UYU 49.5; extras 5/8/9 EUR; markups 1.7/1.5/1.4 for single/double/multi (`CatalogPricingService.java:46-58`). Record type detection recognizes a broader set of double/multi forms (`CatalogPricingService.java:237-251`).

The control workbook instead hardcodes 49.5, 5, 8, 1.6, and 1.4, has no multi setting, and classifies double only when the format begins with `2x` (`InvoiceControlWorkbookService.java:131-165`). `Pedido` also has older default snapshot values of 50, 5, 8, 1.6, 1.4 (`Pedido.java:113-131`), while `PedidoService.calcularItem()` reads the live singleton settings, not those snapshots (`PedidoService.java:294-302`). The SQL seed selects `NEAREST_10` rounding (`docs/migraciones/017_pricing_settings_and_catalog_mode.sql:26-36`), the in-code fallback selects `NONE` (`CatalogPricingService.java:46-53,508-518`), and `calculate()` currently multiplies to `finalPriceUyu` without applying either rule (`CatalogPricingService.java:199-234`). Thus the persisted price, displayed order configuration, generated workbook, and configured rounding rule can disagree.

## 4. Discogs Pipeline

### 4.1 Single URL import

```text
DiscogsTab.LinkIndividual.buscar()
  -> POST /importaciones/discogs/desde-link
  -> DiscogsImportService.fetchDesdeLink()
  -> DiscogsEnrichmentService.enrich()
     -> DiscogsLinkParser.parse()
     -> DiscogsApiClient.fetch(new session)
        -> release fetch OR master -> main_release -> release fetch
     -> DiscogsCoverService.download()
  -> DiscoImportPreviewDTO returned to editable UI
  -> user clicks save
  -> POST /importaciones/discogs/guardar
  -> DiscogsImportService.guardar()
     -> always new Disco
     -> QR synchronization
     -> audio/YouTube previews
     -> pre-sale matching
```

Locations: `DiscogsTab.jsx:127-200`, `ImportacionController.java:82-96`, `DiscogsImportService.java:41-65`, `DiscogsEnrichmentService.java:20-38`.

Discogs API characteristics:

- Link parsing supports release/master URLs, localized/sell URLs, `[r123]`/`[m123]`, and typed IDs (`DiscogsLinkParser.java:13-67`).
- API calls are globally serialized through a semaphore and throttled. HTTP 429 is retried with `Retry-After`/exponential delay (`DiscogsApiClient.java:174-233`).
- Other HTTP errors, timeouts, and network failures are returned immediately without retry (`DiscogsApiClient.java:189-224`).
- A master resolves to `main_release`; the metadata is fetched from the concrete release.
- The mapping keeps only the first artist, genre, style, label, and catalogue number, and reduces format to `VINILO`, `CD`, or `CASSETTE` (`DiscogsApiClient.java:155-167,321-355`).

The save method performs no existing-release lookup at all. Every successful save constructs a new `Disco` (`DiscogsImportService.java:50-64`). Re-saving the same preview or importing the same release in another tab therefore creates another catalogue product and another physical copy set.

For master URLs, the preview stores the normalized **master URL**, even though metadata came from its resolved release (`DiscogsEnrichmentService.java:34-37`; `DiscogsImportService.java:99-105`). This conflicts with the bulk flow's canonical release URL.

### 4.2 Excel/bulk import

```text
DiscogsTab.ExcelLinks.fetchExcel()
  -> POST /importaciones/discogs/jobs
  -> DiscogsImportJobService.createJob()
     -> SHA-256 source fingerprint
     -> DiscogsExcelParser.parse(first sheet)
     -> persistent DiscogsImportJob + DiscogsImportRow staging rows
     -> submit to single-thread jobExecutor
  -> processJob()/processRows()
     -> parse/resolve release or master with one ImportSession cache
     -> save metadata transaction
     -> download cover and save result transaction
     -> finalize job
  -> UI polls persistent job
  -> user retries rows or clicks import identifiable rows
  -> DiscogsImportJobService.importParsedRows()
     -> one transaction per row
     -> exact re-upload row idempotency check
     -> findExistingDisco()
     -> create or merge Disco
     -> QR synchronization
     -> audio/YouTube previews
     -> row linked to catalogue product
  -> optional asynchronous cover ZIP
```

Locations: `DiscogsTab.jsx:217-415,418-692`, `DiscogsImportJobService.java:57-140,208-285,486-571`, `DiscogsExcelParser.java`.

Excel parsing:

- Reads only the first sheet (`DiscogsExcelParser.java:29`).
- Searches for a header within physical rows 0-30 (`DiscogsExcelParser.java:200-245`).
- Detects literal URLs, cell hyperlinks, `HYPERLINK()` formulas, IDs, and common column aliases.
- Preserves duplicate rows as distinct physical copies by design.
- Ignores blank rows and stores unknown column values in observations.
- Maps both `genre` and `style` aliases into one `genre` slot; the second recognized column is silently ignored (`DiscogsExcelParser.java:223-224`).
- Does not recognize direct year/country/label/catalogue-number/format columns. Those are available only after a successful API lookup or in free-text observations.
- A row containing only the recognized `code` field is treated as blank because `code` is absent from `rowHasMeaningfulData()`'s field list (`DiscogsExcelParser.java:248-260`).

Bulk identity and copies:

- A resolved release is matched to a previously imported staged row or exact canonical `https://www.discogs.com/release/{id}` URL (`DiscogsImportJobService.java:934-961`).
- A linked but unresolved row is deliberately not merged (`DiscogsImportJobService.java:964-969`).
- Linkless rows fall back to exact case-insensitive artist/title and loosely compatible format (`DiscogsImportJobService.java:971-982`).
- Repeated rows for one release merge into one `Disco` and increment available quantity by one per row (`DiscogsImportJobService.java:985-988`). QR synchronization then creates the physical-copy records.
- Re-upload idempotency is based on exact file SHA-256 plus the same physical Excel row number (`DiscogsImportJobService.java:241-254`; `DiscogsImportRowRepository.java:43-56`). Editing/reordering/resaving the workbook changes the identity boundary.

Failure behavior:

- A 429-exhausted or other metadata failure is set to `catalogImportStatus=READY` (`DiscogsImportJobService.java:592-623,735-739`).
- `canCreateMeaningfulCatalogProduct()` treats the mere presence of a Discogs source ID as sufficient (`DiscogsImportJobService.java:1125-1127`).
- Such a row can become a placeholder `Disco` named `Discogs pendiente` / `Metadata pendiente ...` (`DiscogsImportJobService.java:1073-1094`).
- The UI explicitly tells the user these rows may be imported with available information and its main action imports every ready row (`DiscogsTab.jsx:524-527,681-683`).

### 4.3 Does bulk use the same working enrichment logic as single import?

**Partially, not completely.** Both ultimately use `DiscogsApiClient`, `DiscogsLinkParser`, `DiscogsCoverService`, the same API DTO (`FetchResult`), and QR/audio services. The single path packages parse + API + cover in `DiscogsEnrichmentService`. Despite that class's comment claiming it is the boundary for both paths (`DiscogsEnrichmentService.java:8-10`), `DiscogsImportJobService` directly orchestrates `apiClient.fetch()` and `coverService.download()` (`DiscogsImportJobService.java:541-567`) and never calls `DiscogsEnrichmentService`.

The important divergences are:

| Behavior | Single URL | Excel/bulk |
|---|---|---|
| Failed metadata | returns an error preview; UI cannot save it | marks linked row `READY`; placeholder can be imported |
| Release identity before save | none | resolved release lookup + staging history |
| Master URL stored | source master URL | canonical resolved release URL |
| API session cache | new session per lookup | one session per job/retry batch |
| Row/status persistence | none | durable staging with row-level statuses |
| Retry | API 429 only; user restarts | API 429 plus explicit row/pending retry UI |
| Catalogue save unit | one transaction for new `Disco` | one transaction per staged row |

Thus bulk does reuse the low-level API parsing but does **not** execute the same success/failure gate or the same persistence identity logic.

## 5. Shared Import Logic

### 5.1 Actually shared

- `Disco`, `DiscoRepository`, `DiscoQrCopyService`, `AudioPreviewService`, and `PreVentaCodeMatcher` are common persistence utilities.
- `CatalogPricingService` is used by Vinyl Future catalogue paths and order calculations.
- Discogs single and bulk share `DiscogsApiClient`, `DiscogsLinkParser`, `DiscogsCoverService`, and `TrackInfo`.
- Vinyl Future primary and staged-order enrichment share `PdfInvoiceParser`, `VinylFutureSearchService`, `VinylFutureScraperService`, and `VinylPageData`.
- `ImportMetadataNormalizer` normalizes display/source labels, but it is not the release-identity normalizer.

### 5.2 Duplicated or divergent

- Catalogue matching/upsert exists separately in `ImportController.findExistingDisco()/mergeDisco()`, `PedidoService.upsertDisco()`, `VinylFutureImportService.confirmarImport()`, `DiscogsImportService.guardar()`, and `DiscogsImportJobService.findExistingDisco()/mergeDisco()`.
- Discogs enrichment orchestration is duplicated between `DiscogsEnrichmentService` and `DiscogsImportJobService`.
- Vinyl Future async orchestration exists in both `ImportController` and `PedidoService`, with the same nested-pool design flaw.
- Source identifiers have different semantics: Vinyl Future stores its page URL in `Disco.discogsUrl`; Discogs single stores a source release/master URL; Discogs bulk stores a canonical resolved release URL.
- Quantity policy diverges: add (primary PDF and Discogs bulk), overwrite (legacy `Pedido`), skip (Vinyl Future Excel), always create a new product (Discogs single).
- Error policy diverges: persist basic Vinyl Future items on metadata miss; mark legacy enrichment successful on miss; block Discogs single; import placeholders in Discogs bulk.

## 6. Database Model

### 6.1 Relevant tables and relationships

| Table/entity | Role | Relationship/identity currently enforced |
|---|---|---|
| `disco` / `Disco` | Catalogue product and available quantity | PK `id_disco`; unique only `codigo_qr`; no unique supplier code or Discogs ID/URL |
| `disco_qr_copy` / `DiscoQrCopy` | Physical stock copy | unique QR and unique `(id_disco, copy_number)` |
| `pedido` / `Pedido` | Invoice/order header | PK only; index, not unique, on `(origen_importacion, numero_factura)` |
| `pedido_item` / `PedidoItem` | Original invoice line | FK to order and optional catalogue product; no line uniqueness |
| `discogs_import_job` | Persistent bulk job | PK; indexed but non-unique source fingerprint |
| `discogs_import_row` | Persistent Excel physical row | FK to job and optional product; no unique `(job,row)` or release identity |
| `catalog_audio_preview` | Imported/manual preview | no natural unique key preventing duplicates |
| `shipping_order` | Receiving projection | unique generated order number, but number generation is count-based |

`DiscoQrCopy` does not use a JPA relationship for `idDisco`, but the manual schema script supplies the database relationship. This does not itself cause the observed imports, but it weakens object-model navigation.

### 6.2 Duplicate prevention at database level

The database protects physical-copy numbering and QR codes. It does **not** protect any of these business identities:

- Vinyl Future invoice `(source, invoice number)`;
- Vinyl Future product `(supplier, normalized catalogue code, edition)`;
- Discogs concrete release ID;
- canonical Discogs release URL;
- source file/job fingerprint;
- physical source row `(fingerprint, row number)`.

`docs/migraciones/022_pedidos_vinylfuture_source.sql:7-8` creates a normal index, not a unique constraint. `docs/migraciones/027_discogs_import_fingerprint.sql:4-5` also creates a normal index. Entity definitions likewise contain no corresponding uniqueness (`Disco.java:27-28,120-121`; `Pedido.java:26-36`; `DiscogsImportJob.java:29-30`; `DiscogsImportRow.java:30-55`). Duplicate prevention is therefore application-only except for QR-copy mechanics.

### 6.3 Schema lifecycle risk

Production validation requires someone to apply the manual scripts before boot (`application-prod.properties:28`). Development and tests build schema from entities (`application-dev.properties:10`; `src/test/resources/application.properties:6`). There is no automated replay proving that every numbered SQL script creates the same schema that production validates, and duplicate sequence numbers exist (`010`, `021`, `025`). This can produce environment-dependent import behavior, particularly as staging columns were added across scripts 007, 010, 015, 026-028, 039-041.

### 6.4 Domain consistency conclusion

The data model is capable of distinguishing one product from many copies. The import layer lacks a single `release_identity`/source-key model and treats `cantidadCopias` as both an aggregate to mutate and an instruction to create QR rows. The QR uniqueness constraints prevent the same `(product, copy_number)` twice, but they cannot prevent two `Disco` rows representing the same release, each with its own valid copies.

## 7. Duplicate Risk Analysis

| Finding | File / class / method | Reason | Severity / confidence | Probable result |
|---|---|---|---|---|
| C-01 | `DiscogsImportService.java:50-64`, `guardar()` | Always maps preview to a new entity; no lookup or idempotency check | CRITICAL / CONFIRMED | Duplicate `Disco` and copy rows on repeated save |
| C-02 | `Disco.java:27-28,120-121`; `Pedido.java:26-36`; migrations 022/027 | No unique product, release, invoice, or job-row business key; all checks are raceable | CRITICAL / HIGHLY LIKELY | Concurrent duplicate orders, releases, and copy sets |
| H-02 | `ImportController.java:483-511,638-645` | repository code lookup is exact while other matching normalizes accent/punctuation/space | HIGH / CONFIRMED | `ABC-123` and `ABC 123` split; permissive fallback can also merge editions |
| H-07 | `DiscogsEnrichmentService.java:34-37`; `DiscogsImportJobService.java:946-961,1052-1058` | single master saves master URL; bulk identity is resolved release URL | HIGH / CONFIRMED | Same concrete release duplicated across Discogs paths |
| H-10 | `VinylFutureImportService.java:119-124` | exact duplicate code is skipped, while blank/variant codes create new products | HIGH / CONFIRMED | Either lost copies or duplicate catalogue rows |
| M-07 | `ShippingOrderService.java:80-121` | order number is `count + 1`; rows map to first invoice item by code | MEDIUM / HIGHLY LIKELY | concurrent number collision; duplicated/mispriced shipping rows |
| M-08 | `DiscogsTab.jsx:127-200,350-374`; APIs have no idempotency key | UI disables a button within one component state, but another tab/retry can repeat requests | MEDIUM / CONFIRMED structural risk | repeat imports reach unprotected backend |
| L-02 | five separate upsert implementations listed in section 5 | duplicated policy evolves independently | LOW / CONFIRMED smell | regressions differ by endpoint |

Duplicate categories must remain separate:

- **Catalogue duplicate:** two `disco` rows for one edition/release. C-01/C-02/H-02/H-07 directly permit this.
- **Physical-copy duplicate:** extra valid QR-copy rows caused by duplicate catalogue imports or an erroneous quantity increment. QR constraints do not detect cross-`Disco` duplication.
- **Order-item duplicate:** two `pedido_item` rows may be legitimate repeated invoice lines; there is no uniqueness or source-line fingerprint. Two concurrent same-invoice orders duplicate all lines.
- **Import-record duplicate:** multiple `discogs_import_job`/row records are always allowed. Exact workbook re-upload is made idempotent only when catalogue import checks the prior fingerprint + row number.

## 8. Missing Item Risk Analysis

| Finding | File / class / method | Reason | Severity / confidence | Probable result |
|---|---|---|---|---|
| H-01 | `PdfInvoiceParser.java:40-46,130-143,200-225` | single-line regex; unmatched/wrapped/invalid rows are silently ignored; first summary header suppresses later lines | HIGH / CONFIRMED | omitted invoice item before any DTO/persistence exists |
| C-04 | `ImportController.java:94,117,133-150,856-914`; `PedidoService.java:47,332-349` | parent tasks wait for children submitted to same full fixed pool | CRITICAL / CONFIRMED condition | job/enrichment remains indefinitely running; items appear missing |
| C-03 | `ImportController.java:136-160,264-283`; `PedidoService.java:143-205` | order commits separately and blocks retry even if later catalogue/ZIP work fails | CRITICAL / CONFIRMED | order exists but catalogue item(s) never complete; same invoice cannot be retried normally |
| H-03 | `DiscogsImportJobService.java:592-623,1125-1127` | failures are not skipped but become incomplete placeholders; from a complete-catalogue perspective the real item is absent | HIGH / CONFIRMED | expected enriched record never appears |
| H-05 | `ImportController.java:342-400`; `VinylFutureImportService.java:113-152`; `DiscogsImportService.java:67-78` | exceptions are caught inside transactions; a later flush/rollback can undo apparent successes | HIGH / HIGHLY LIKELY | returned counters/list disagree with committed data, or partial batch commits |
| H-10 | `VinylFutureImportService.java:119-124` | a second physical copy with an existing code is `continue`d rather than merged | HIGH / CONFIRMED | stock copy disappears |
| H-11 | `DiscogsApiClient.java:174-224`; `DiscogsCoverService.java:49-74` | only 429 is retried; cover download is one attempt | HIGH / CONFIRMED | some large-job metadata/covers are absent after transient errors |
| M-02 | `DiscogsExcelParser.java:248-260` | code-only row is treated as blank; only first sheet is parsed | MEDIUM / CONFIRMED | valid source row never reaches staging |
| M-11 | `VinylFutureImportService.java:290-299`; Discogs request DTO/controller | zero quantity and some negative/invalid values are not uniformly rejected | MEDIUM / CONFIRMED | zero-copy catalogue product or row failure |

The most dangerous missing-item point is the PDF boundary: once an unmatched line is omitted, downstream counts can look internally consistent with the shortened list. The primary endpoint does not abort when `cantidadTotalPdf != sum(parsed quantities)`; only the order upload response exposes a warning (`PedidoService.java:129-139`), and only control-workbook generation rejects it (`InvoiceControlWorkbookService.java:91, validate()`).

## 9. Metadata Loss Analysis

| Finding | Location | Lost/degraded information | Confidence |
|---|---|---|---|
| H-03 | `DiscogsImportJobService.java:592-623,1073-1094` | all API-derived fields can be replaced by placeholder artist/title | CONFIRMED |
| H-09 | `VinylFutureSearchService.java:49,57-60`; `VinylFutureScraperService.java:47,52-69` | transient search/scrape failures are cached as `Optional.empty()` for process lifetime | CONFIRMED |
| H-11 | `DiscogsApiClient.java:174-224`; `DiscogsCoverService.java:49-74` | non-429 transient failure is not retried; local cover can remain missing | CONFIRMED |
| M-01 | `DiscogsApiClient.java:155-167,321-355` | only first artist/genre/style/label/catalogue number; edition format detail and multi-value fields discarded | CONFIRMED |
| M-02 | `DiscogsExcelParser.java:211-235` | genre/style share one slot; year/country/label/catalogue number/format lack recognized columns | CONFIRMED |
| M-03 | `DiscoImportPreviewDTO.java:16-42`; `DiscogsImportService.java:81-111` | `FetchResult.catalogNumber()` has no preview field and is dropped in single import | CONFIRMED |
| M-04 | `AudioPreviewService.java:29-57` | old auto previews are deleted before new tracks are filtered for playable URLs | CONFIRMED |
| M-05 | `VinylFutureScraperService.java:121-122`; `ImportController.java:464-479` | style is folded into genre; no `estilo` assignment; Vinyl Future source URL is written into `discogs_url` | CONFIRMED |
| M-06 | `VinylFutureAssetService.java:173-209,335-359`; `DiscogsCoverService.java:49-74` | sanitized/truncated target collision may reuse stale file; Discogs cover validates size, not image MIME/content | POSSIBLE / CONFIRMED validation gap |

Metadata gaps do not generally roll back Vinyl Future products. This is why a product can exist without year, genre, country, image, or previews. In Discogs single import a failed API response blocks saving, but a cover failure still yields a preview using the remote image URL and a warning. In bulk, even metadata failure is considered importable.

## 10. Transaction / Partial Persistence Risks

### C-03 — Vinyl Future has multiple commit domains

**CONFIRMED.** `runVinylFutureJob()` wraps `processImport()` in an outer `TransactionTemplate` (`ImportController.java:133-143`). `processImport()` immediately calls `PedidoService.persistirVinylFuture()` (`ImportController.java:271-272`), which is `REQUIRES_NEW` (`PedidoService.java:143`). That order and its items commit independently.

Consequences:

1. If the later catalogue duplicate check rejects the invoice, the new order already exists. This is particularly reachable when older catalogue rows carry the invoice number but no matching `Pedido` (`ImportController.java:279-283`).
2. If scraping/assets/catalogue/shipping fails the outer transaction, the order remains.
3. The ZIP is built after the outer database transaction returns (`ImportController.java:147-158`). A ZIP failure marks the job failed while order and catalogue are committed.
4. A retry then encounters the already-committed order check in `persistirVinylFuture()` (`PedidoService.java:149-152`) and cannot repair the supposedly failed import through the same endpoint.

### H-05 — caught exceptions inside broad transactions

**HIGHLY LIKELY risk.** The primary Vinyl Future loop catches each item exception and continues (`ImportController.java:342-390`), while the entire network and persistence workflow is one transaction. Some JPA errors occur only on flush/commit, outside that catch. Others can mark the transaction rollback-only even when caught. Shipping-order exceptions are also swallowed (`ImportController.java:393-400`). The job can count successful rows before final commit, then fail or roll them all back.

`VinylFutureImportService.confirmarImport()` and `DiscogsImportService.guardarLote()` use the same catch-and-continue pattern inside one transaction (`VinylFutureImportService.java:113-152`; `DiscogsImportService.java:67-78`). By contrast, Discogs bulk catalogue import correctly gives each row a fresh `TransactionTemplate` (`DiscogsImportJobService.java:208-225`), providing the best row isolation of the current paths.

### Long-running database transaction

The primary Vinyl Future outer transaction includes supplier searching, scraping, retries, media downloads, and every item save (`ImportController.java:264-400`). After the invoice-existence query, it can retain transactional resources for the full network duration. This increases lock/connection pressure and makes rollback much more expensive.

### Files are not transactional

- Original PDF is written before/order around database persistence (`PedidoService.java:103-106,181-183`). A later rollback can leave an orphan file.
- Vinyl Future assets and Discogs covers may remain after database rollback.
- A database row can point to a file later removed externally.
- ZIP/batch state is in memory or local storage and cannot participate in a DB rollback.

### Asynchronous boundaries

- Primary Vinyl Future job state and result batches are memory-only (`ImportController.java:95-109`; `VinylFutureImportBatchService.java:22-48`). Restart loses status and download handles while committed DB work remains.
- Discogs job/row state is durable, but executor work is not. No startup recovery scans `PENDING`, `PROCESSING`, or ZIP-preparing jobs; restart can strand them (`DiscogsImportJobService.java:57,59-132,486-509`).

## 11. Vinyl Future Findings

### C-03 — non-atomic order/catalogue/export lifecycle

Severity: **CRITICAL**. Confidence: **CONFIRMED**. See section 10. This is the strongest explanation for a job reported failed while some database records exist, and for a later retry being rejected.

### C-04 — nested fixed-pool starvation

Severity: **CRITICAL**. Confidence: **CONFIRMED condition**.

`ImportController` has one four-thread pool (`ImportController.java:94`). Each job itself runs on that pool (`:117`) and waits for search/scrape/asset futures that it submits to the same pool (`:868-883,901-914`). With four concurrent jobs, all four workers can be occupied by parent jobs, leaving no worker for any child future; every parent waits indefinitely. With three jobs only one worker services all children, making imports appear intermittently slow.

The legacy enrichment path repeats the design with a three-thread pool: an outer coordinator occupies a pool thread and waits for per-item futures on the same pool (`PedidoService.java:47,332-349`). Three concurrent orders can starve it completely.

### H-01 — parser omissions are silent and non-fatal

Severity: **HIGH**. Confidence: **CONFIRMED**.

Product parsing requires code, artist/title separators, price, quantity, and total on one extracted line (`PdfInvoiceParser.java:40-46`). Wrapped descriptions, layout changes, or OCR-like extraction do not match and return `null` without a row-level warning (`:200-206`). Invalid amounts are debug-only skips (`:209-225`). Once any summary header is seen, every subsequent text line is ignored for item parsing (`:130-143`). Multi-page happy cases are tested, but wrapped/malformed row refusal is not.

### H-02 — inconsistent product identity and normalization

Severity: **HIGH**. Confidence: **CONFIRMED**.

`findExistingDisco()` uses exact case-sensitive repository code lookup (`ImportController.java:483-489`; `DiscoRepository.java:39-40`). `mergeExactRepeatedRows()` and `strongMatch()` use a different normalizer that removes accents and punctuation and collapses spaces (`ImportController.java:606-645`). The legacy order path and Excel path also use exact code. Consequently code variants can split one release. Conversely, a raw code match wins without checking label, format, artist, country, or year, so the same supplier code reused for another edition can merge distinct releases.

### H-06 — legacy order import overwrites stock

Severity: **HIGH**. Confidence: **CONFIRMED**.

`PedidoService.upsertDisco()` finds an exact code and then unconditionally sets `cantidadCopias` to the incoming order quantity (`PedidoService.java:404-424`). Importing two additional copies into an existing stock of three sets stock to two rather than five. QR synchronization can then retire/alter the physical-copy projection to match the lower quantity. This is a direct catalogue/copy loss path specific to the legacy endpoint.

### H-08 — pricing and control workbook disagree

Severity: **HIGH**. Confidence: **CONFIRMED**.

Live defaults/settings, `Pedido` snapshots, and control workbook constants disagree as described in section 3.5. The workbook's formula recognizes only leading `2x` and cannot represent `MULTI`; the backend recognizes more formats. SQL seeds `NEAREST_10`, the Java fallback uses `NONE`, and the calculation applies no rounding rule. A user can therefore see a different final UYU price in the workbook than the catalogue import persisted. `actualizarConfiguracion()` ignores request values and copies the live singleton (`PedidoService.java:271-284`), making the endpoint DTO misleading.

### H-09 — negative caching and ineffective global throttle

Severity: **HIGH**. Confidence: **CONFIRMED**.

Both search and scrape caches store `Optional.empty()` (`VinylFutureSearchService.java:49,57-60`; `VinylFutureScraperService.java:47,52-69`). A temporary timeout/404 becomes a process-lifetime miss for that key. Search calls sleep per thread, but the primary path runs multiple searches in parallel; there is no shared permit/next-request timestamp analogous to Discogs. It can burst the supplier despite `delay-ms=400`, increasing transient failures that then become negative-cache entries.

### H-10 — Excel copy semantics are wrong

Severity: **HIGH**. Confidence: **CONFIRMED**.

The Excel flow skips an existing exact code (`VinylFutureImportService.java:119-124`) instead of adding the physical copy. Code-less records and punctuation/case variants are always new. It neither preserves an invoice source row nor enriches the product, making repair/audit difficult.

### M-05/M-06/M-07 — metadata/assets/shipping projections

- **M-05:** Vinyl Future style is folded into genre and the supplier page URL is stored in a field named `discogsUrl` (`VinylFutureScraperService.java:121-122`; `ImportController.java:464-471`). Cross-source identity becomes semantically polluted.
- **M-06:** media folder identity is a sanitized/truncated code/artist/album string, and any existing non-empty target is reused without comparing source or content (`VinylFutureAssetService.java:173-186,335-359`). Two names that sanitize/truncate identically can share stale assets. **POSSIBLE** collision.
- **M-07:** shipping rows search the first original invoice line with matching code for every imported product (`ShippingOrderService.java:93-112`). If multiple price-separated lines eventually target the same `Disco`, quantities/prices can be repeated from the first line. Number generation uses `count + 1` despite a unique number (`:80-85`), creating a concurrent race.

## 12. Discogs Findings

### C-01 — single import always creates a new catalogue product

Severity: **CRITICAL**. Confidence: **CONFIRMED**.

`DiscogsImportService.guardar()` maps every preview to a new `Disco`, generates a QR, saves, synchronizes copies, and saves again (`DiscogsImportService.java:50-64`). It never calls `DiscoRepository.findByDiscogsUrl()`, never checks a release ID, and has no idempotency token. Reproduction: fetch release 123, save it, repeat the same actions; two catalogue rows result. The database accepts both because `discogs_url` and `codigo_interno` are not unique.

### C-02 — no database release identity or atomic claim

Severity: **CRITICAL**. Confidence: **HIGHLY LIKELY race; CONFIRMED schema gap**.

Bulk does check for a prior resolved release, but the query and insert are not protected by a unique constraint or lock (`DiscogsImportJobService.java:934-988`). Two application instances or a single import concurrent with single-URL save can both observe no record and insert. Each row and its QR copies are internally valid, so no constraint catches the duplicate release.

### H-03 — failed metadata is deliberately importable

Severity: **HIGH**. Confidence: **CONFIRMED**.

Every linked API failure is made catalogue-ready (`DiscogsImportJobService.java:592-623,735-739`), and the presence of `discogsId` satisfies the meaningful-product check (`:1125-1127`). Placeholder generation (`:1073-1094`) means a temporary outage can permanently create an incomplete product. A later metadata retry can update a row-linked product, but only if users find and retry the staging job; the catalogue entry initially looks like a real import.

### H-04 — persistent staging without persistent execution recovery

Severity: **HIGH**. Confidence: **CONFIRMED**.

Jobs and rows survive restart; the executor submission does not (`DiscogsImportJobService.java:57,59-132`). There is no `@PostConstruct`, scheduled recovery, lease, heartbeat, or queue. A restart during processing leaves status `PROCESSING`/stage values that the frontend continues to poll but no worker resumes. Cover ZIP preparation has the same local executor/filesystem dependency.

### H-07 — master/release canonicalization differs by path

Severity: **HIGH**. Confidence: **CONFIRMED**.

Single import stores the normalized input URL, so a master stays `.../master/{id}` (`DiscogsEnrichmentService.java:37`; `DiscogsImportService.java:104`). Bulk converts resolved identity to `.../release/{resolvedId}` (`DiscogsImportJobService.java:1052-1058`) and intentionally refuses a master URL as a concrete release identity (`:950-968`). The bulk lookup therefore cannot find a single-imported master record by canonical release URL.

### H-11 — retry coverage is narrower than failure surface

Severity: **HIGH**. Confidence: **CONFIRMED**.

Discogs 429 handling is thoughtful: serialized calls, header-aware delay, and bounded retry. But HTTP 500/502/503, connection resets, and timeouts do not retry (`DiscogsApiClient.java:174-224`). Cover download also has one attempt and returns a remote fallback/warning (`DiscogsCoverService.java:49-74`). On a large job, independent transient failures naturally produce only some metadata/covers. No pagination bug was found because release/master endpoints are single-resource fetches; the project does not import paginated Discogs collections.

### M-01/M-03/M-04 — lossy metadata and preview replacement

- **M-01:** API mapping keeps first values and coarse format, losing contributors and edition details.
- **M-03:** single preview DTO has no catalogue-number field, so the API catalogue number disappears. Bulk preserves it only in `DiscogsImportRow.catalogNumber` and later appends it to free-text notes (`DiscogsImportJobService.java:995-999`), not a dedicated catalogue column.
- **M-04:** `AudioPreviewService.guardarDesdeTracks()` deletes existing Vinyl Future/Discogs auto previews before selecting playable new tracks (`AudioPreviewService.java:29-57`). A retry whose returned track list is non-empty but contains no playable URLs erases previously good previews.

## 13. Excel Import Findings

### Discogs Excel

1. **Physical rows are intentionally copies.** Duplicate release IDs within one workbook merge to one `Disco` and add one available copy per row. Existing tests cover this and it matches the domain model.
2. **Exact-workbook replay protection is narrow.** The idempotency key is `(SHA-256 bytes, physical row number)`, not a durable source-row UUID or import command key (`DiscogsImportJobService.java:241-254`). Resaving/reordering the workbook makes the same physical inventory look new. There is no database unique constraint even for this narrow key.
3. **Column recognition loses structured data.** `genre` and `style` collide; first wins (`DiscogsExcelParser.java:223-224`). Year, country, label, catalogue number, and format are not direct mapped columns. Unknown values survive only in observations and are not used to build fields.
4. **Code-only rows disappear.** `rowHasMeaningfulData()` omits the `code` field (`DiscogsExcelParser.java:248-260`).
5. **First-sheet/header-window assumptions.** Only sheet 0 is read, and the header must occur in the first 31 physical rows. A valid later sheet or decorated workbook can be rejected/ignored. This is **M-12 / CONFIRMED limitation**.
6. **All source sale states become available used inventory.** Bulk always sets `CondicionDisco.USADO` and `EstadoDisco.DISPONIBLE`; spreadsheet SOLD/RESERVED remain notes/status for review (`DiscogsImportJobService.java:894-925`). The UI states this policy. It is intentional, but users expecting a historical migration may see sold items reintroduced as available stock.
7. **Rows are isolated at catalogue persistence.** `importParsedRows()` uses a new transaction per row. This is the strongest implementation in the repository for avoiding whole-batch rollback.
8. **Processing is serialized.** One job executor and one API request permit protect rate limits but make a large workbook block all later jobs. This is delay, not omission, unless a restart strands the job.

### Vinyl Future Excel

1. Only first sheet, row 0 header (`VinylFutureImportService.java:89-105`).
2. Exact duplicate code is skipped, not converted into more copies (`:119-124`).
3. Quantity zero passes validation because only `< 0` is rejected (`:290-299`).
4. No supplier enrichment, order/invoice link, file fingerprint, row id, or retry record is persisted.
5. Per-row exceptions are swallowed inside a shared transaction (`:113-152`).
6. The UI implementation exists but is not rendered, so this is an accessible API/legacy path rather than the default user workflow (`VinylFutureTab.jsx:67-238,449-454`).

## 14. Frontend Findings

### Requests and loading guards

- Vinyl Future primary starts one asynchronous job on button click and polls every 1.5 seconds (`VinylFutureTab.jsx:270-313`). While loading, the idle button is no longer rendered. This protects ordinary double clicks in one mounted component.
- Discogs single disables search/save during `loading`/`saving` (`DiscogsTab.jsx:114,175-180`).
- Discogs bulk replaces the upload UI during loading and disables catalogue import while processing/saving (`DiscogsTab.jsx:427-454,681-683`).
- `React.StrictMode` is enabled (`frontend/src/main.jsx:1-12`), but import POSTs are event handlers, not mount effects. StrictMode is **not** a credible direct cause of duplicate imports. The polling effects clean up timers/cancellation.

### M-08 — frontend does not provide durable idempotency

Severity: **MEDIUM**. Confidence: **CONFIRMED**.

The UI state guards do not cover double tabs, refresh-after-uncertain-response, network/client retries, or two operators. None of the POSTs sends an idempotency key (`sonograma.js:303-318,428-480`). This becomes a duplicate cause only because backend/DB identity is weak; the frontend alone is not the primary defect.

### Job recovery asymmetry

- Discogs bulk persists the active job ID in `localStorage` and can reload it (`DiscogsTab.jsx:217-238,317-348`). The backend job record is durable, although its execution is not restart-safe.
- Vinyl Future job ID is not durably persisted by backend or frontend. Server restart makes polling return 404 even when database writes committed.

### Error handling

The frontend displays top-level errors and row statuses well, but it cannot reconcile a failed response with whether a write committed. There is no “find by source invoice/release and resume” workflow. The Discogs warning explicitly encourages importing rate-limited rows with partial data (`DiscogsTab.jsx:524-527`), reinforcing H-03 rather than accidentally causing it.

## 15. Error Handling Findings

### Silent continuation points

| Location | Handling | Possible outcome |
|---|---|---|
| `PdfInvoiceParser.java:200-225` | unmatched/invalid line returns null; debug log at most | missing item |
| `VinylFutureSearchService.java:123-126` | IO failure -> cached empty | persisted item without metadata |
| `VinylFutureScraperService.java:57-70` | any exception -> cached empty | persisted item without metadata |
| `ImportController.java:386-400` | item and shipping errors logged, loop/job continues | partial import or later transaction rollback |
| `PedidoEnrichmentService.java:34-48` | no search/scrape result still marked `ENRICHED` | false success/incomplete metadata |
| `PedidoService.java:371-395` | per-item failures counted and continued | partial legacy order import |
| `VinylFutureImportService.java:148-150` | row save failure logged only | missing Excel row; response lacks row error |
| `DiscogsImportService.java:67-78` | batch save failure logged only | partial/rollback-ambiguous batch |
| `DiscogsImportJobService.java:592-623` | API failure becomes ready warning | placeholder catalogue product |
| `DiscogsImportJobService.java:880-891` | audio error becomes warning | product without previews |
| `DiscogsCoverService.java:70-74` | download failure returns fallback | remote-only/missing local cover |

### Retry mechanisms

- Vinyl Future media: three bounded attempts with content-type/size validation (`VinylFutureAssetService.java:213-250`). Good local behavior.
- Vinyl Future search/scrape: no retry and negative-cache failure for process lifetime.
- Discogs metadata: retries only 429; explicit bulk retry endpoints exist.
- Discogs cover: no automatic retry; row status says retryable in some exception paths, but ordinary `CoverResult.failure` is finalized as a warning.
- Database writes: no safe command idempotency; repeating after an uncertain outcome can duplicate or be blocked in a partial state.

### M-09 — counters/statuses can mislead

Severity: **MEDIUM**. Confidence: **CONFIRMED**.

- Primary Vinyl Future increments success/copy counters before outer commit (`ImportController.java:342-390`); commit can still fail.
- Existing Vinyl Future QR count adds purchased quantity, not actual newly created QR rows (`ImportController.java:354-358`).
- Discogs `rowsImported` includes associated/already-imported rows; UI has improved labels but status semantics remain non-obvious.
- Discogs jobs usually end `COMPLETED_WITH_WARNINGS`; a metadata failure made ready is not a job error (`DiscogsImportJobService.java:780-790`).
- Legacy enrichment calls a miss `ENRICHED`.

### L-04 — error/logging quality

Severity: **LOW**. Confidence: **CONFIRMED**.

Bulk Discogs logging has useful job/row/stage identifiers. Other paths often log only a message and item title. `GlobalExceptionHandler.handleRuntime()` returns raw exception messages as HTTP 500 (`GlobalExceptionHandler.java:82-86`), which is inconsistent and can expose internals without giving the client a stable error code. There is no shared import correlation/idempotency ID across order, catalogue, files, and shipping projection.

## 16. Most Probable Root Causes

This is the authoritative finding register. Counts are based on these 32 IDs.

### CRITICAL (4)

| ID | Confidence | Evidence and exact location | Why it produces observed behavior |
|---|---|---|---|
| C-01 | CONFIRMED | `DiscogsImportService.guardar()`, `DiscogsImportService.java:50-64` | Every single-URL save creates a new release and copy set, so repeat saves directly duplicate catalogue data. |
| C-02 | CONFIRMED schema gap; HIGHLY LIKELY race | `Disco.java:27-28,120-121`; `Pedido.java:26-36`; migrations `022:7-8`, `027:4-5`; bulk lookup `DiscogsImportJobService.java:934-988` | No DB-enforced invoice/release/source identity makes all find-then-save deduplication raceable and cross-flow duplicates valid. |
| C-03 | CONFIRMED | `ImportController.java:133-160,264-283`; `PedidoService.java:143-205` | The order commits independently, catalogue commits later, ZIP runs after commit; a failed job can leave partial durable state and a blocked retry. |
| C-04 | CONFIRMED condition | `ImportController.java:94,117,856-914`; `PedidoService.java:47,332-349` | Parent jobs occupy every pool worker while waiting for child tasks on that same pool, causing intermittent indefinite stalls. |

### HIGH (12)

| ID | Confidence | Evidence and exact location | Why it produces observed behavior |
|---|---|---|---|
| H-01 | CONFIRMED | `PdfInvoiceParser.java:40-46,130-143,200-225`; non-fatal mismatch `PedidoService.java:129-139` | Wrapped/malformed PDF lines disappear silently and primary import proceeds with a shortened item list. |
| H-02 | CONFIRMED | `ImportController.java:483-511,606-645`; `DiscoRepository.java:39-54` | Exact code, normalized merge keys, and descriptive fallback disagree, causing both false splits and false merges. |
| H-03 | CONFIRMED | `DiscogsImportJobService.java:592-623,735-739,1073-1094,1125-1127`; UI `DiscogsTab.jsx:524-527` | API failures are explicitly catalogue-ready, so incomplete placeholder products are expected. |
| H-04 | CONFIRMED | `ImportController.java:95-109`; `VinylFutureImportBatchService.java:22-48`; `DiscogsImportJobService.java:57,59-132,486-509` | Memory-only jobs vanish; persistent Discogs jobs have no worker recovery after restart, leaving stuck/inconsistent operations. |
| H-05 | HIGHLY LIKELY | `ImportController.java:342-400`; `VinylFutureImportService.java:113-152`; `DiscogsImportService.java:67-78` | Catching persistence exceptions inside a wider transaction can yield partial commits, rollback-only commits, or success counters for rolled-back work. |
| H-06 | CONFIRMED | `PedidoService.upsertDisco()`, `PedidoService.java:404-424` | Existing stock is overwritten with incoming quantity instead of incremented, making copies disappear. |
| H-07 | CONFIRMED | `DiscogsEnrichmentService.java:34-37`; `DiscogsImportService.java:99-105`; `DiscogsImportJobService.java:934-968,1052-1058` | Single master and bulk release use different identities for the same concrete release. |
| H-08 | CONFIRMED | `CatalogPricingService.java:46-58,199-251,508-518`; migration `017:26-36`; `Pedido.java:113-131`; `InvoiceControlWorkbookService.java:131-165`; `PedidoService.java:271-302` | Prices, rounding, and simple/double/multi classification vary between database, order DTO, control workbook, and catalogue. |
| H-09 | CONFIRMED | `VinylFutureSearchService.java:49-60,96-126`; `VinylFutureScraperService.java:47-70`; parallel calls `ImportController.java:286-313` | Transient failures persist in negative caches; parallel per-thread delays can trigger more supplier failures. |
| H-10 | CONFIRMED | `VinylFutureImportService.java:113-152,290-299` | Existing codes are skipped rather than added as copies; blank/variant codes create duplicates; zero-copy records are accepted. |
| H-11 | CONFIRMED | `DiscogsApiClient.java:174-224`; `DiscogsCoverService.java:49-74` | Only 429 retries; transient HTTP/network/cover failures create the “some rows/some covers” pattern on large jobs. |
| H-12 | CONFIRMED process gap | no Flyway dependency in `pom.xml`; SQL only in `docs/migraciones`; `application-prod.properties:28`; `REGISTRO_ENTREGA_FINAL.md:19` | Manual, unversioned schema application can make import tables/constraints differ by environment and prevents reliable migration replay. |

### MEDIUM (12)

| ID | Confidence | Evidence and exact location | Why it produces observed behavior |
|---|---|---|---|
| M-01 | CONFIRMED | `DiscogsApiClient.java:155-167,321-355` | Multi-valued Discogs metadata and edition details are reduced to first/coarse values. |
| M-02 | CONFIRMED | `DiscogsExcelParser.java:211-260` | Genre/style collide; key columns are not structured; code-only rows are treated blank. |
| M-03 | CONFIRMED | `DiscoImportPreviewDTO.java:16-42`; `DiscogsImportService.java:81-111` | Single import drops Discogs catalogue number completely. |
| M-04 | CONFIRMED | `AudioPreviewService.java:29-57` | A retry can delete good auto previews before discovering the replacement set has no playable links. |
| M-05 | CONFIRMED | `VinylFutureScraperService.java:121-122`; `ImportController.java:464-471,551-560` | Vinyl Future style is lost as a separate field and its URL pollutes the Discogs URL field. |
| M-06 | POSSIBLE collision; CONFIRMED validation gap | `VinylFutureAssetService.java:173-209,335-359`; `DiscogsCoverService.java:49-74` | Sanitized path collisions/stale reuse or non-image cover content can attach wrong/missing assets. |
| M-07 | HIGHLY LIKELY | `ShippingOrderService.java:80-121` | Count-based number generation races; first-code row matching can duplicate wrong quantity/price. |
| M-08 | CONFIRMED structural risk | `VinylFutureTab.jsx:270-313`; `DiscogsTab.jsx:127-200,350-374`; `sonograma.js` import calls | Loading guards are local UI state; no end-to-end idempotency covers tabs, retries, or uncertain responses. |
| M-09 | CONFIRMED | `ImportController.java:342-390`; `PedidoEnrichmentService.java:34-48`; `DiscogsImportJobService.java:780-790` | Success/status counters can describe attempted rather than committed/complete work. |
| M-10 | CONFIRMED lifecycle gap | `PedidoService.java:103-106,181-183`; `VinylFutureImportBatchService.java:22-88`; local cover/media services | Files and temporary exports are not transactionally reconciled with database rows; restart/rollback creates orphans/stale URLs. |
| M-11 | CONFIRMED | `VinylFutureImportService.java:290-299`; `ImportacionController.java:92-95`; preview DTO lacks bean constraints | Import endpoints do not consistently validate quantities, negative prices, edited IDs/URLs, or required fields server-side. |
| M-12 | CONFIRMED limitation | `DiscogsExcelParser.java:29,200-245`; `VinylFutureImportService.java:89-105` | First-sheet and fixed header-window/row assumptions can ignore or reject otherwise valid workbook data. |

### LOW (4)

| ID | Confidence | Evidence and exact location | Why it matters |
|---|---|---|---|
| L-01 | CONFIRMED | no import references to `MovimientoStock`; imports mutate `Disco` and QR rows directly | Stock changes lack one shared movement/audit trail, making diagnosis and reconciliation harder. |
| L-02 | CONFIRMED | separate upserts in `ImportController`, `PedidoService`, `VinylFutureImportService`, `DiscogsImportService`, `DiscogsImportJobService` | Duplicated logic is the structural reason policies drift, though it is not alone a data-corruption event. |
| L-03 | POSSIBLE | JPA entities including `Disco`, `Pedido`, job/row use Lombok `@Data` | Mutable/all-field `equals/hashCode` can behave badly in sets/maps or with lazy relationships; no direct import failure was proven. |
| L-04 | CONFIRMED | `GlobalExceptionHandler.java:82-86`; inconsistent log context across import services | Raw 500 messages and missing common correlation IDs make uncertain outcomes harder to diagnose safely. |

## 17. Suspicious Code Locations

| Severity | File | Method | Problem | Possible Symptom |
|---|---|---|---|---|
| CRITICAL | `service/importacion/DiscogsImportService.java:50` | `guardar()` | unconditional entity creation | same release imported twice |
| CRITICAL | `controller/ImportController.java:133` | `runVinylFutureJob()` | transaction commits before ZIP; job failure conflates export and import | failed UI job with committed records |
| CRITICAL | `service/PedidoService.java:143` | `persistirVinylFuture()` | `REQUIRES_NEW` before rest of import | orphan order, retry blocked |
| CRITICAL | `controller/ImportController.java:856` | `parallelMap()` | submits to same pool as waiting parent | import hangs under concurrency |
| CRITICAL | `service/PedidoService.java:332` | `lanzarEnriquecimiento()` | same nested-pool pattern | enrichment never finishes |
| HIGH | `service/PdfInvoiceParser.java:130` | `parseInvoice()` | unparseable rows ignored; summary switches parsing off | omitted item |
| HIGH | `controller/ImportController.java:483` | `findExistingDisco()` | exact supplier code precedes weak descriptive match | false split/merge |
| HIGH | `service/importacion/DiscogsImportJobService.java:592` | `handleMetadataFailure()` | failure becomes catalogue-ready | placeholder metadata |
| HIGH | `service/importacion/DiscogsImportJobService.java:934` | `findExistingDisco()` | good lookup but no unique/atomic claim | concurrent duplicate release |
| HIGH | `service/PedidoService.java:424` | `upsertDisco()` | replaces stock quantity | missing copies |
| HIGH | `service/importacion/VinylFutureImportService.java:119` | `confirmarImport()` | skips duplicate code | missing copies |
| HIGH | `service/VinylFutureSearchService.java:57` | `buscar()` | caches failure forever | repeated missing metadata |
| HIGH | `service/importacion/DiscogsApiClient.java:174` | `request()` | retry condition only 429 | intermittent missing metadata |
| HIGH | `service/InvoiceControlWorkbookService.java:131` | `fillItems()`/`fillSummary()` | hardcoded stale pricing/type rules | inconsistent cost/final price |
| MEDIUM | `service/importacion/DiscogsExcelParser.java:223` | `detectHeader()` | style and genre map to same key | lost style |
| MEDIUM | `service/AudioPreviewService.java:29` | `guardarDesdeTracks()` | delete-before-validate replacement | previews disappear |
| MEDIUM | `service/ShippingOrderService.java:76` | `crearDesdeImport()` | first code match and count-based number | wrong/duplicate shipping rows |
| MEDIUM | `service/VinylFutureAssetService.java:173` | `store()` | filename treated as content identity | wrong/stale cover or MP3 |
| MEDIUM | `service/importacion/DiscogsImportJobService.java:57` | job executor | durable rows, ephemeral worker | stuck job after restart |
| LOW | `exception/GlobalExceptionHandler.java:82` | `handleRuntime()` | raw exception text, no stable import error code | poor client recovery/diagnosis |

## 18. Vinyl Future vs Discogs Comparison

| Area | Vinyl Future | Discogs | Risk |
|---|---|---|---|
| Parsing | PDFBox line regex; separate Excel parser; permissive skips | robust URL parser; first-sheet Excel alias parser | VF can lose lines; Discogs can ignore sheets/code-only rows |
| Identification | supplier code exact, then artist/title/format/label | single: none; bulk: resolved release ID/canonical URL | identity is inconsistent within and across sources |
| Deduplication | invoice/order app check; product exact/fallback; Excel skip | single none; bulk staging/fingerprint/release lookup | no database atomic guarantee in either system |
| Metadata enrichment | supplier search + HTML scrape + MP3; failure persists base item | API release/master + YouTube + cover; single blocks metadata failure, bulk imports placeholder | different failure semantics explain different symptoms |
| Persistence | primary broad transaction plus `REQUIRES_NEW` order; two legacy paths | single one transaction; bulk one transaction per catalogue row | VF partial lifecycle; single duplicates; bulk isolation is better |
| Stock creation | add, overwrite, or skip depending endpoint | single creates new product; bulk adds one per physical Excel row | release/copy concepts are applied inconsistently |
| Error handling | many caught exceptions and negative caches | detailed staged statuses, but failures made ready | silent missing metadata vs explicit incomplete placeholder |
| Transactions | network/files inside long transaction; ZIP after commit | single synchronous; bulk staged and row-isolated | VF rollback ambiguity; cross-flow Discogs race remains |
| Retry logic | assets retry; search/scrape do not | 429 retry and user row retry; no general HTTP/cover retry | intermittent external failure remains visible in catalogue |
| Logging | useful summaries but weaker durable correlation | bulk logs job/row/stage well; single less so | reconstructing cross-table outcome is difficult |
| Async/restart | job/results memory-only; nested four-thread pool | job rows durable, executor ephemeral and single-threaded | VF loses status; Discogs can strand status |
| Pricing | active settings plus stale order/workbook variants | manual price from Excel; no cost calculation from Discogs | VF inconsistent calculated prices; Discogs sparse acquisition cost |

The shared architectural problem is **lack of a durable import command and canonical product identity enforced by the database**. The source-specific problems differ: Vinyl Future is primarily parser/transaction/executor fragile; Discogs single is primarily missing deduplication; Discogs bulk is primarily failure-gating/retry/recovery fragile.

## 19. Recommended Fix Order

No fix is implemented by this audit. The safest later order is:

1. **Define product and source-operation identities before touching code.** Decide the concrete edition key: Discogs resolved release ID; Vinyl Future supplier + normalized catalogue code plus explicit edition discriminators. Decide whether invoice number is unique per provider. Document the one-release/N-copy invariant.
2. **Audit and reconcile existing duplicates before adding constraints.** Build read-only reports for duplicate resolved Discogs URLs/IDs, normalized Vinyl Future codes, invoice pairs, quantities versus QR copies, and staged rows. Choose merge rules and preserve sales/history links.
3. **Add schema-level uniqueness and atomic claim semantics.** Only after cleanup, add durable source identity columns/constraints and transaction-safe upserts. Protect invoice import commands and Discogs release identity, not artist/title alone.
4. **Make every endpoint use the same catalogue identity/upsert component.** Route Discogs single and bulk through one release-aware save policy; route all Vinyl Future paths through one normalized supplier-product/copy policy. Preserve separate UI workflows if needed.
5. **Repair Vinyl Future operation boundaries.** Persist a durable import operation, validate the parsed invoice before any durable write, separate network staging from short DB transactions, and make export failure distinct from catalogue failure. Provide resumable/idempotent retry.
6. **Replace nested executor designs and add restart recovery.** Use separate orchestration/work pools or structured bounded execution; persist/reclaim jobs with leases/status transitions. Keep Discogs rate-aware serialization without using process memory as truth.
7. **Tighten parsing and validation gates.** Reconcile PDF item counts/totals before import; report every rejected source line; validate edited previews, quantities, prices, and URLs server-side; make workbook sheet/header choice explicit.
8. **Unify enrichment success policy and retry strategy.** Do not silently call misses successful. Decide whether incomplete products are prohibited or visibly staged for manual approval. Retry transient 5xx/network/cover failures with bounded backoff; do not cache transient failures indefinitely.
9. **Unify pricing and format classification.** Make the control workbook consume the active settings/snapshot, include multi-disc handling, and remove stale duplicate constants.
10. **Reconcile files and secondary projections.** Give assets content/source identities, validate cover MIME, make audio replacement non-destructive, correct shipping-row mapping, and define cleanup/recovery for orphan files/ZIPs.
11. **Add the regression suite in section 20 and run it against PostgreSQL migrations.** A production-like migration replay/concurrency suite should gate release before legacy paths are retired.

## 20. Tests That Should Exist

### Identity and duplicate tests

1. Single Discogs release imported twice yields one `Disco` and the intended copy count/idempotent result.
2. Discogs master imported by single URL, then its resolved release imported by Excel, yields one catalogue product.
3. Same Discogs release concurrently imported by two requests/application instances; database permits only one product identity.
4. Exact Discogs workbook uploaded/imported twice remains idempotent.
5. Same physical inventory workbook resaved/reordered has a defined, tested result rather than accidental duplication.
6. Discogs Excel duplicate rows for one release yield one product and N physical copies.
7. Same Vinyl Future invoice submitted sequentially and concurrently yields one order/import operation.
8. Vinyl Future codes `ABC-123`, `abc-123`, `ABC 123`, accented/punctuated variants follow the documented identity policy.
9. Same catalogue code with different label/format/year follows the documented edition policy without false merge.
10. Cross-endpoint test: primary PDF, legacy `Pedido`, and Excel import cannot apply different stock semantics.

### Missing/parsing tests

11. Multi-page Vinyl Future invoice with repeated headers/summary only after final item imports every line.
12. Wrapped artist/title/product lines either parse correctly or block import with exact source-line diagnostics.
13. Quantity summary mismatch blocks catalogue/order commit in the chosen policy.
14. Malformed price, missing quantity, zero/negative quantity, and inconsistent line total have explicit outcomes.
15. PDF with zero detected items fails before order creation.
16. Discogs workbook with decorated preamble, header after row 30, data on a non-first sheet, formula/hyperlink cells, blank rows, and code-only rows.
17. Large Excel file verifies every physical row reaches a terminal status and no array/index overwrite occurs.

### External failure and metadata tests

18. Vinyl Future search timeout followed by recovery retries rather than retaining a negative cache forever.
19. Vinyl Future page mismatch persists only permitted base fields and produces a durable review status.
20. Missing image and missing MP3/YouTube do not remove an otherwise valid item.
21. Audio retry with no playable replacement preserves prior valid previews.
22. Discogs 429 honors `Retry-After`, terminates after the configured bound, and resumes safely.
23. Discogs 500/502/timeout/connect reset exercise the intended transient retry policy.
24. Discogs metadata unavailable cannot create an unapproved placeholder, or does so only under an explicit tested manual-approval policy.
25. Cover response with HTML/wrong MIME/oversize/truncated data is rejected; a later retry repairs it.
26. Multiple artists/genres/styles/labels/formats/catalogue numbers exercise the chosen lossless mapping.

### Transactions, concurrency, and recovery tests

27. Four concurrent primary Vinyl Future jobs complete within a bound; no pool starvation.
28. Three concurrent legacy enrichment jobs complete within a bound.
29. Forced failure after order persistence, after first catalogue row, during QR creation, during shipping creation, and during ZIP build produces a defined resumable state.
30. Database constraint failure inside a per-row loop verifies response counters equal committed rows.
31. Application restart during Vinyl Future search/catalogue/ZIP and Discogs metadata/cover/import/ZIP resumes or reaches a clear recoverable status.
32. File write succeeds then DB fails, and DB succeeds then file/ZIP fails; reconciliation/cleanup is verified.
33. Two concurrent shipping-order creations cannot generate the same number.
34. Quantity aggregate always equals available `DiscoQrCopy` rows after add, sale/reserve, import retry, and rollback.

### Pricing and schema tests

35. Single, double (`2xLP`, `2LP`, variants), and multi/box formats yield the same type and price in backend, order snapshot, and generated workbook.
36. Active pricing settings changed from defaults are reflected consistently in catalogue and control workbook.
37. All manual SQL migrations replay in order on PostgreSQL from an empty database, then Hibernate `validate` passes.
38. Upgrade from a representative production snapshot applies every import-table migration and verifies required unique constraints/indexes.

### Existing-test gaps

Current import tests establish happy-path parsing, assets, ZIP layout, duplicate physical rows within Discogs bulk, exact-workbook replay, master caching, and 429 retry. They should be retained. The missing tests above concentrate on cross-flow behavior, failures after partial writes, real concurrency, restart, and production schema—the conditions not represented by the current H2 unit/integration baseline.

## 21. Final Diagnosis

### 1. Why can items currently be duplicated?

Because single-URL Discogs always inserts, every import path lacks a database-enforced canonical release/source identity, and matching rules differ by path. Exact URL/code variations, master-vs-release URLs, edited/reordered files, multiple tabs, retries after uncertain responses, and concurrent find-then-save operations can all pass application checks. QR uniqueness protects copies only within one `Disco`; it cannot recognize two `Disco` rows as the same release.

### 2. Why can items disappear or not be imported?

Vinyl Future PDF lines can fail a strict one-line regex and be silently skipped; the primary path does not reject quantity mismatch. Legacy Vinyl Future Excel skips existing codes instead of adding copies, and legacy order import can overwrite stock with a lower incoming quantity. Executor starvation can leave jobs permanently in progress. Caught persistence exceptions and non-durable/restart-stranded jobs can also leave source rows without the expected catalogue result.

### 3. Why can metadata be incomplete?

Vinyl Future persists base invoice items when search/scrape/assets fail, caches transient misses indefinitely, and does not retry search/scrape. Discogs retries only 429, covers get one attempt, bulk explicitly imports metadata failures as placeholders, and the API/DTO/Excel mappings discard multi-valued or unsupported fields. Audio replacement can delete previous good previews.

### 4. Same architectural issue or different issues?

Both share one architectural flaw: no canonical, database-enforced product/import identity used by every endpoint. Beyond that, the dominant failures differ. Vinyl Future is most fragile in PDF validation, transaction lifecycle, executor coordination, and divergent stock/pricing paths. Discogs single has a direct missing-dedup bug; Discogs bulk has better staging and row transactions but unsafe failure gating, incomplete retry, cross-flow URL identity, and no restart worker recovery.

### 5. Is the problem parsing, matching, persistence, concurrency, API handling, or a combination?

It is a combination. The ranking is: **identity/matching and database enforcement first; transaction/idempotency second; concurrency/recovery third; parsing validation fourth; external API retry/metadata mapping fifth.** None of those layers alone explains every symptom.

### 6. What should be fixed first?

1. Establish and enforce canonical release/source-operation identities, then make Discogs single and bulk plus every Vinyl Future path use the same atomic upsert/copy policy.
2. Make the primary Vinyl Future import one durable, resumable operation with validation before writes and distinct catalogue/export outcomes.
3. Replace same-pool nested async work and add restart recovery for both job systems.
4. Prevent incomplete Discogs metadata from becoming an ordinary catalogue product without explicit review; broaden transient retries.
5. Make PDF/Excel parsing, quantity reconciliation, pricing, and format rules fail visibly and consistently, backed by PostgreSQL concurrency/migration regression tests.
