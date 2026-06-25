package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.dto.VinylFutureImportSummaryDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.CsvExportService;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.CatalogPricingService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.PdfInvoiceParser;
import com.sonograma.service.ShippingOrderService;
import com.sonograma.service.VinylFutureScraperService;
import com.sonograma.service.VinylFutureSearchService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.VinylFutureImportBatchService;
import com.sonograma.service.ZipBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
@RestController
@RequestMapping("/importar")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final PdfInvoiceParser pdfParser;
    private final VinylFutureSearchService vinylFutureSearch;
    private final VinylFutureScraperService vinylFutureScraper;
    private final VinylFutureAssetService vinylFutureAssetService;
    private final CsvExportService csvExport;
    private final ZipBundleService zipBundle;
    private final DiscoRepository discoRepository;
    private final ShippingOrderService shippingOrderService;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final CatalogPricingService catalogPricingService;
    private final VinylFutureImportBatchService importBatchService;
    private final ExecutorService importPool = Executors.newFixedThreadPool(4);

    /**
     * Primary PDF flow: imports complete records into the catalog without forcing a download.
     */
    @PostMapping("/vinylfuture-catalogo")
    @Transactional
    public ResponseEntity<VinylFutureImportSummaryDTO> importarFacturaAlCatalogo(
            @RequestParam MultipartFile file) {
        try {
            ImportProcessingResult result = processImport(file);
            String importId = importBatchService.store(
                csvExport.buildCsv(result.searchResults()),
                result.pageDataMap()
            );
            return ResponseEntity.ok(result.summary().withImportId(importId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + ex.getMessage(), ex);
        }
    }

    /**
     * Secondary export flow. It keeps the historical CSV/assets ZIP available on demand.
     */
    @PostMapping("/vinylfuture-csv")
    @Transactional
    public ResponseEntity<StreamingResponseBody> procesarFactura(@RequestParam MultipartFile file) {
        ImportProcessingResult result;
        try {
            result = processImport(file);
        } catch (IllegalArgumentException ex) {
            return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IOException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + e.getMessage());
        }

        return buildZipResponse(csvExport.buildCsv(result.searchResults()), result.pageDataMap());
    }

    @GetMapping("/vinylfuture/{importId}/zip")
    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> exportarZipDesdeImport(@PathVariable String importId) {
        VinylFutureImportBatchService.ImportBatch batch = importBatchService.find(importId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No se encontró la importación solicitada. Volvé a procesar el PDF."
            ));
        return buildZipResponse(batch.csv(), batch.pageDataMap());
    }

    private ResponseEntity<StreamingResponseBody> buildZipResponse(
            String csv,
            Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) {
        Path zipPath;
        try {
            zipPath = zipBundle.buildZip(csv, pageDataMap);
        } catch (Exception e) {
            log.error("Error al construir el ZIP: {}", e.getMessage(), e);
            return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al generar el archivo: " + e.getMessage()
            );
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "vinylfuture-import-" + timestamp + ".zip";

        long zipSize;
        try {
            zipSize = Files.size(zipPath);
            log.info("ZIP generado: {} bytes.", zipSize);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException cleanupError) {
                log.warn("No se pudo limpiar el ZIP temporal: {}", cleanupError.getMessage());
            }
            return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo preparar el archivo generado"
            );
        }

        StreamingResponseBody body = outputStream -> {
            try {
                Files.copy(zipPath, outputStream);
            } finally {
                Files.deleteIfExists(zipPath);
            }
        };

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(zipSize)
            .body(body);
    }

    private ImportProcessingResult processImport(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío");
        }

        log.info("Recibido PDF '{}' ({} bytes). Iniciando importación al catálogo.",
            file.getOriginalFilename(), file.getSize());
        ParsedInvoice invoice = pdfParser.parseInvoice(file.getBytes());
        List<InvoiceItem> items = invoice.items();

        long searchStarted = System.nanoTime();
        Map<InvoiceItem, Optional<String>> searchResults = parallelMap(items, vinylFutureSearch::buscar);
        long found = searchResults.values().stream().filter(Optional::isPresent).count();
        log.info("Búsqueda completada en {} ms. {}/{} ítems encontrados en Vinyl Future.",
            elapsedMillis(searchStarted), found, items.size());

        long scrapeStarted = System.nanoTime();
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap = parallelMap(
            items,
            item -> searchResults.getOrDefault(item, Optional.empty()).flatMap(vinylFutureScraper::scrape),
            (item, pageData, processed, total) -> log.info(
                "VinylFuture product page processed {}/{}: item='{}', found={}",
                processed,
                total,
                itemReference(item),
                pageData.isPresent()
            )
        );
        log.info("Scraping completado en {} ms.", elapsedMillis(scrapeStarted));

        long assetsStarted = System.nanoTime();
        int coversFound = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .filter(page -> !blank(page.frontImageUrl()))
            .count();
        int mp3Found = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .flatMap(page -> page.tracks() == null ? java.util.stream.Stream.empty() : page.tracks().stream())
            .filter(track -> !blank(track.mp3Url()))
            .count();
        int youtubeFound = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .flatMap(page -> page.tracks() == null ? java.util.stream.Stream.empty() : page.tracks().stream())
            .filter(track -> !blank(track.youtubeUrl()))
            .count();

        AssetProcessingResult assetResult = storeAssetsWithProgress(pageDataMap);
        pageDataMap = assetResult.pageDataMap();
        log.info("Assets Vinyl Future procesados en {} ms.", elapsedMillis(assetsStarted));

        List<Disco> imported = new ArrayList<>();
        int skippedDuplicates = 0;
        int qrEntriesCreated = 0;
        for (Map.Entry<InvoiceItem, Optional<VinylPageData>> entry : pageDataMap.entrySet()) {
            InvoiceItem item = entry.getKey();
            Optional<VinylPageData> pageData = entry.getValue();
            String catalogCode = pageData.map(VinylPageData::code)
                .filter(value -> !value.isBlank())
                .orElse(item.codigoCatalogo());
            if (catalogCode != null && !catalogCode.isBlank()
                    && discoRepository.findByCodigoInterno(catalogCode).isPresent()) {
                skippedDuplicates++;
                log.info("Disco ya importado, se omite: {} / {}", catalogCode, item.album());
                continue;
            }

            try {
                Disco disco = buildDisco(item, pageData.orElse(null), catalogCode);
                disco = discoRepository.save(disco);
                qrEntriesCreated += qrCopyService.synchronize(disco).size();
                Disco savedDisco = disco;
                pageData.ifPresent(page ->
                    audioPreviewService.guardarDesdeTracks(savedDisco.getIdDisco(), page.tracks()));
                imported.add(discoRepository.save(disco));
                int mp3Count = pageData.map(page -> (int) safeTracks(page).stream()
                    .filter(track -> !blank(track.mp3Url()))
                    .count()).orElse(0);
                int youtubeCount = pageData.map(page -> (int) safeTracks(page).stream()
                    .filter(track -> !blank(track.youtubeUrl()))
                    .count()).orElse(0);
                log.info(
                    "Vinyl Future importado: title='{} - {}', sourceUrl='{}', cover={}, mp3Previews={}, youtubeLinks={}, dbUpdated=true",
                    disco.getArtista(),
                    disco.getAlbum(),
                    pageData.map(VinylPageData::sourceUrl).orElse(null),
                    pageData.map(page -> !blank(page.frontImageUrl())).orElse(false),
                    mp3Count,
                    youtubeCount
                );
            } catch (Exception ex) {
                log.warn("No se pudo guardar en BD: {} - {}: {}",
                    item.artista(), item.album(), ex.getMessage());
            }
        }

        if (!imported.isEmpty()) {
            try {
                shippingOrderService.crearDesdeImport(imported, invoice.items(), invoice.total());
                log.info("ShippingOrder creada con {} ítems.", imported.size());
            } catch (Exception ex) {
                log.warn("No se pudo crear ShippingOrder: {}", ex.getMessage());
            }
        }

        Map<InvoiceItem, Optional<VinylPageData>> finalPageDataMap = pageDataMap;
        List<String> failedLinks = items.stream()
            .filter(item -> searchResults.getOrDefault(item, Optional.empty()).isEmpty()
                || finalPageDataMap.getOrDefault(item, Optional.empty()).isEmpty())
            .map(item -> firstNonBlank(
                item.codigoCatalogo(), item.artista() + " - " + item.album()))
            .distinct()
            .toList();

        VinylFutureImportSummaryDTO summary = new VinylFutureImportSummaryDTO(
            null,
            items.size(),
            imported.size(),
            coversFound,
            assetResult.coversDownloaded(),
            mp3Found,
            assetResult.mp3Downloaded(),
            youtubeFound,
            qrEntriesCreated,
            assetResult.failedMediaDownloads(),
            failedLinks.size(),
            skippedDuplicates,
            0,
            failedLinks
        );
        log.info(
            "Resumen Vinyl Future: detectados={}, importados={}, portadasEncontradas={}, portadasDescargadas={}, "
                + "mp3Encontrados={}, mp3Descargados={}, mediaFallida={}, youtube={}, qr={}, fallidos={}, duplicados={}, rateLimit={}",
            summary.recordsDetected(), summary.recordsImported(), summary.coversFound(),
            summary.coversDownloaded(), summary.mp3PreviewsFound(), summary.mp3Downloaded(),
            summary.failedMediaDownloads(), summary.youtubeLinksFound(), summary.qrEntriesCreated(),
            summary.failedLinks(), summary.skippedDuplicates(), summary.rateLimitFailures()
        );
        return new ImportProcessingResult(summary, searchResults, pageDataMap);
    }

    private Disco buildDisco(InvoiceItem item, VinylPageData page, String catalogCode) {
        String format = page != null ? firstNonBlank(page.format(), item.formato()) : item.formato();
        java.math.BigDecimal cost = item.precioUnitario() != null
            ? item.precioUnitario()
            : (page != null ? page.purchasePrice() : null);
        CatalogPricingService.PricingResult pricing = catalogPricingService.calcular(cost, format);

        Disco disco = new Disco();
        disco.setArtista(page != null ? firstNonBlank(page.artist(), item.artista()) : item.artista());
        disco.setAlbum(page != null ? firstNonBlank(page.title(), item.album()) : item.album());
        disco.setCodigoInterno(catalogCode);
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setCodigoQr(UUID.randomUUID().toString());
        disco.setProcedencia("VINYL_FUTURE");
        disco.setTipoDisco(parseFormat(format));
        disco.setCondicion(parseCondition(page != null ? page.condition() : null));
        disco.setCantidadCopias(item.cantidad() != null ? item.cantidad() : 1);
        disco.setCosto(cost);
        disco.setPrecioVenta(pricing != null ? pricing.salePriceUyu() : null);
        if (page != null) {
            disco.setSelloDiscografico(page.label());
            disco.setGenero(page.genre());
            disco.setAnio(page.year());
            disco.setPais(page.country());
            disco.setDescripcion(page.description());
            disco.setImagenUrl(page.frontImageUrl());
            disco.setDiscogsUrl(page.sourceUrl());
            if (!safeTracks(page).isEmpty()) {
                disco.setTracklist(safeTracks(page).stream()
                    .map(track -> firstNonBlank(track.label(), "") + " "
                        + firstNonBlank(track.name(), "Track"))
                    .map(String::strip)
                    .collect(Collectors.joining("\n")));
            }
        }
        return disco;
    }

    private TipoDisco parseFormat(String value) {
        if (blank(value)) return TipoDisco.VINILO;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("CD")) return TipoDisco.CD;
        if (normalized.contains("CASSETTE") || normalized.contains("TAPE")) return TipoDisco.CASSETTE;
        if (normalized.contains("DIGITAL")) return TipoDisco.DIGITAL;
        return TipoDisco.VINILO;
    }

    private CondicionDisco parseCondition(String value) {
        if (blank(value)) return CondicionDisco.NUEVO;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("USED") || normalized.contains("USADO")
            ? CondicionDisco.USADO
            : CondicionDisco.NUEVO;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<TrackInfo> safeTracks(VinylPageData page) {
        return page.tracks() == null ? List.of() : page.tracks();
    }

    private record ImportProcessingResult(
        VinylFutureImportSummaryDTO summary,
        Map<InvoiceItem, Optional<String>> searchResults,
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap
    ) {}

    private record AssetProcessingResult(
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap,
        int coversDownloaded,
        int mp3Downloaded,
        int failedMediaDownloads
    ) {}

    private <T> Map<InvoiceItem, T> parallelMap(
            List<InvoiceItem> items,
            Function<InvoiceItem, T> operation) {
        return parallelMap(items, operation, null);
    }

    private <T> Map<InvoiceItem, T> parallelMap(
            List<InvoiceItem> items,
            Function<InvoiceItem, T> operation,
            ProgressLogger<T> progressLogger) {
        AtomicInteger processed = new AtomicInteger();
        int total = items.size();
        Map<InvoiceItem, CompletableFuture<T>> futures = items.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                item -> CompletableFuture.supplyAsync(() -> {
                    T result = operation.apply(item);
                    if (progressLogger != null) {
                        progressLogger.log(item, result, processed.incrementAndGet(), total);
                    }
                    return result;
                }, importPool),
                (first, ignored) -> first,
                LinkedHashMap::new
            ));

        Map<InvoiceItem, T> results = new LinkedHashMap<>();
        futures.forEach((item, future) -> results.put(item, future.join()));
        return results;
    }

    private AssetProcessingResult storeAssetsWithProgress(
            Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) {
        int totalProducts = (int) pageDataMap.values().stream().flatMap(Optional::stream).count();
        if (totalProducts == 0) {
            log.info("Assets Vinyl Future: no hay productos con metadata para descargar.");
            return new AssetProcessingResult(pageDataMap, 0, 0, 0);
        }

        AtomicInteger productsProcessed = new AtomicInteger();
        AtomicInteger coversDownloaded = new AtomicInteger();
        AtomicInteger mp3Downloaded = new AtomicInteger();
        AtomicInteger failedMediaDownloads = new AtomicInteger();
        log.info("Assets Vinyl Future: iniciando descarga persistente de media para {} productos.", totalProducts);

        Map<InvoiceItem, CompletableFuture<Optional<VinylFutureAssetService.AssetStoreResult>>> futures =
            pageDataMap.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> CompletableFuture.supplyAsync(() -> entry.getValue().map(page ->
                        vinylFutureAssetService.storeAssetsWithResult(entry.getKey(), page)
                    ), importPool),
                    (first, ignored) -> first,
                    LinkedHashMap::new
                ));

        Map<InvoiceItem, Optional<VinylPageData>> results = new LinkedHashMap<>();
        futures.forEach((item, future) -> {
            Optional<VinylFutureAssetService.AssetStoreResult> storeResult = future.join();
            storeResult.ifPresent(result -> {
                coversDownloaded.addAndGet(result.coversDownloaded());
                mp3Downloaded.addAndGet(result.mp3Downloaded());
                failedMediaDownloads.addAndGet(result.failedMediaDownloads());
                int processed = productsProcessed.incrementAndGet();
                log.info(
                    "VinylFuture media processed {}/{}: item='{}', coversDownloaded={}, mp3FilesDownloaded={}, failedMediaDownloads={}, totals[covers={}, mp3={}, failed={}]",
                    processed,
                    totalProducts,
                    itemReference(item),
                    result.coversDownloaded(),
                    result.mp3Downloaded(),
                    result.failedMediaDownloads(),
                    coversDownloaded.get(),
                    mp3Downloaded.get(),
                    failedMediaDownloads.get()
                );
            });
            results.put(item, storeResult.map(VinylFutureAssetService.AssetStoreResult::page));
        });

        log.info(
            "Assets Vinyl Future finalizados: productsProcessed={}, coversDownloaded={}, mp3FilesDownloaded={}, failedMediaDownloads={}",
            productsProcessed.get(),
            coversDownloaded.get(),
            mp3Downloaded.get(),
            failedMediaDownloads.get()
        );
        return new AssetProcessingResult(
            results,
            coversDownloaded.get(),
            mp3Downloaded.get(),
            failedMediaDownloads.get()
        );
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String itemReference(InvoiceItem item) {
        return firstNonBlank(item.codigoCatalogo(), item.artista() + " - " + item.album());
    }

    @GetMapping("/vinylfuture/discos/{idDisco}/media-validation")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> validarMediaDisco(@PathVariable Long idDisco) {
        Disco disco = discoRepository.findById(idDisco)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disco no encontrado"));
        List<String> missingFiles = new ArrayList<>();
        List<String> badUrls = new ArrayList<>();
        validateMediaUrl("cover", disco.getImagenUrl(), missingFiles, badUrls);
        for (AudioPreviewDTO preview : audioPreviewService.listarPorDisco(idDisco)) {
            validateMediaUrl(
                "audio " + firstNonBlank(preview.trackPosition(), preview.trackName(), String.valueOf(preview.id())),
                preview.audioUrl(),
                missingFiles,
                badUrls
            );
        }
        return ResponseEntity.ok(Map.of(
            "idDisco", idDisco,
            "codigoInterno", disco.getCodigoInterno(),
            "valid", missingFiles.isEmpty() && badUrls.isEmpty(),
            "missingFiles", missingFiles,
            "badUrls", badUrls
        ));
    }

    private void validateMediaUrl(
            String label,
            String url,
            List<String> missingFiles,
            List<String> badUrls) {
        if (blank(url)) return;
        Path path = vinylFutureAssetService.localPath(url);
        if (path == null) {
            badUrls.add(label + ": " + url);
            return;
        }
        if (!Files.isRegularFile(path)) {
            missingFiles.add(label + ": " + path);
        }
    }

    @GetMapping("/vinylfuture/media/{*filename}")
    public ResponseEntity<Resource> vinylFutureMedia(@PathVariable String filename) throws IOException {
        filename = decodeMediaPath(filename);
        Resource resource = vinylFutureAssetService.load(filename);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(vinylFutureAssetService.contentType(filename)))
            .body(resource);
    }

    private String decodeMediaPath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    @PreDestroy
    void shutdownImportPool() {
        importPool.shutdown();
    }

    private ResponseEntity<StreamingResponseBody> errorResponse(HttpStatus status, String message) {
        byte[] payload = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StreamingResponseBody body = outputStream -> outputStream.write(payload);
        return ResponseEntity.status(status)
            .contentType(MediaType.TEXT_PLAIN)
            .contentLength(payload.length)
            .body(body);
    }

    @FunctionalInterface
    private interface ProgressLogger<T> {
        void log(InvoiceItem item, T result, int processed, int total);
    }
}
