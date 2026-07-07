package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.dto.VinylFutureImportJobDTO;
import com.sonograma.dto.VinylFutureImportJobItemDTO;
import com.sonograma.dto.VinylFutureImportJobStartDTO;
import com.sonograma.dto.VinylFutureImportSummaryDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.enums.VinylFutureImportJobStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private final PlatformTransactionManager transactionManager;
    private final ExecutorService importPool = Executors.newFixedThreadPool(4);
    private final Map<String, VinylFutureJobState> vinylFutureJobs = new ConcurrentHashMap<>();

    /**
     * Primary PDF flow: imports complete records into the catalog without forcing a download.
     */
    @PostMapping("/vinylfuture-catalogo")
    public ResponseEntity<VinylFutureImportJobStartDTO> importarFacturaAlCatalogo(
            @RequestParam MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo vacío");
        }
        String jobId = UUID.randomUUID().toString();
        VinylFutureJobState job = new VinylFutureJobState(jobId);
        job.currentStep = "Factura recibida";
        vinylFutureJobs.put(jobId, job);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + ex.getMessage(), ex);
        }
        importPool.submit(() -> runVinylFutureJob(job, bytes, file.getOriginalFilename()));
        return ResponseEntity.accepted().body(new VinylFutureImportJobStartDTO(
            jobId,
            "/importar/vinylfuture/jobs/" + jobId
        ));
    }

    @GetMapping("/vinylfuture/jobs/{jobId}")
    public ResponseEntity<VinylFutureImportJobDTO> obtenerJobVinylFuture(@PathVariable String jobId) {
        VinylFutureJobState job = vinylFutureJobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Importación VinylFuture no encontrada: " + jobId);
        }
        return ResponseEntity.ok(job.toDto());
    }

    private void runVinylFutureJob(VinylFutureJobState job, byte[] bytes, String filename) {
        try {
            job.markRunning("Leyendo factura");
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            ImportProcessingResult result = tx.execute(status -> {
                try {
                    return processImport(bytes, filename, job);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("No se pudo leer el PDF: " + ex.getMessage(), ex);
                }
            });
            if (result == null) {
                throw new IllegalStateException("No se pudo completar la importación");
            }
            job.updateStep("Preparando ZIP", 92);
            String importId = importBatchService.store(
                csvExport.buildCsv(result.searchResults()),
                result.pageDataMap(),
                buildZipRootName(result.invoice())
            );
            VinylFutureImportSummaryDTO summary = result.summary().withImportId(importId);
            job.complete(summary);
        } catch (Exception ex) {
            log.error("Falló job VinylFuture {}: {}", job.jobId, ex.getMessage(), ex);
            job.fail(ex.getMessage());
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
            result = processImport(file.getBytes(), file.getOriginalFilename(), null);
        } catch (IllegalArgumentException ex) {
            return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IOException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + e.getMessage());
        }

        return buildZipResponse(
            csvExport.buildCsv(result.searchResults()),
            result.pageDataMap(),
            buildZipRootName(result.invoice())
        );
    }

    @GetMapping("/vinylfuture/{importId}/zip")
    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> exportarZipDesdeImport(@PathVariable String importId) {
        log.info("Solicitud ZIP VinylFuture importId={}", importId);
        VinylFutureImportBatchService.ImportBatch batch = importBatchService.find(importId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No se encontró la importación solicitada. Volvé a procesar el PDF."
            ));
        log.info("Batch VinylFuture encontrado importId={}, csvBytes={}, products={}",
            importId,
            batch.csv() != null ? batch.csv().getBytes(StandardCharsets.UTF_8).length : 0,
            batch.pageDataMap() != null ? batch.pageDataMap().size() : 0);
        return buildZipResponse(batch.csv(), batch.pageDataMap(), batch.zipRootName());
    }

    private ResponseEntity<StreamingResponseBody> buildZipResponse(
            String csv,
            Map<InvoiceItem, Optional<VinylPageData>> pageDataMap,
            String zipRootName) {
        Path zipPath;
        try {
            zipPath = zipBundle.buildZip(csv, pageDataMap, zipRootName);
        } catch (Exception e) {
            log.error("Error al construir el ZIP: {}", e.getMessage(), e);
            return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al generar el archivo: " + e.getMessage()
            );
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = sanitizeFilename(firstNonBlank(zipRootName, "VinylFuture_Invoice_" + timestamp)) + ".zip";

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

    private ImportProcessingResult processImport(byte[] bytes, String filename, VinylFutureJobState job) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Archivo vacío");
        }

        log.info("Recibido PDF '{}' ({} bytes). Iniciando importación al catálogo.",
            filename, bytes.length);
        ParsedInvoice invoice = pdfParser.parseInvoice(bytes);
        List<InvoiceItem> invoiceItems = invoice.items();
        if (job != null) {
            job.setInvoice(invoice);
            job.setItems(invoiceItems);
            job.updateStep("Validando productos", 15);
        }
        if (invoice.numeroFactura() != null && !invoice.numeroFactura().isBlank()
                && discoRepository.existsByNumeroFacturaCompra(invoice.numeroFactura())) {
            throw new IllegalArgumentException("La factura " + invoice.numeroFactura()
                + " ya fue importada. Se bloqueó la importación para evitar duplicados.");
        }
        List<InvoiceItem> items = mergeExactRepeatedRows(invoiceItems, job);

        long searchStarted = System.nanoTime();
        if (job != null) job.updateStep("Buscando metadatos", 25);
        Map<InvoiceItem, Optional<String>> searchResults = parallelMap(items, vinylFutureSearch::buscar);
        long found = searchResults.values().stream().filter(Optional::isPresent).count();
        log.info("Búsqueda completada en {} ms. {}/{} ítems encontrados en Vinyl Future.",
            elapsedMillis(searchStarted), found, items.size());

        long scrapeStarted = System.nanoTime();
        if (job != null) job.updateStep("Buscando metadatos", 35);
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap = parallelMap(
            items,
            item -> searchResults.getOrDefault(item, Optional.empty())
                .flatMap(url -> vinylFutureScraper.scrape(url)
                    .filter(page -> strongMatch(item, page, url))
                    .or(() -> {
                        String reason = "Metadata ambigua o débil para " + itemReference(item);
                        log.warn("{} url={}", reason, url);
                        if (job != null) job.warnItem(item, reason);
                        return Optional.empty();
                    })),
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
        if (job != null) job.updateStep("Descargando portadas", 50);
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

        if (job != null) job.updateStep("Descargando audios", 60);
        AssetProcessingResult assetResult = storeAssetsWithProgress(pageDataMap);
        pageDataMap = assetResult.pageDataMap();
        log.info("Assets Vinyl Future procesados en {} ms.", elapsedMillis(assetsStarted));

        if (job != null) job.updateStep("Guardando en catálogo", 72);
        List<Disco> imported = new ArrayList<>();
        int skippedDuplicates = 0;
        int qrEntriesCreated = 0;
        for (Map.Entry<InvoiceItem, Optional<VinylPageData>> entry : pageDataMap.entrySet()) {
            InvoiceItem item = entry.getKey();
            Optional<VinylPageData> pageData = entry.getValue();
            String catalogCode = pageData.map(VinylPageData::code)
                .filter(value -> !value.isBlank())
                .orElse(item.codigoCatalogo());
            try {
                Optional<Disco> existing = findExistingDisco(item, pageData.orElse(null), catalogCode);
                int availableBefore = existing.map(disco -> (int) qrCopyService.countAvailableCopies(disco.getIdDisco()))
                    .orElse(0);
                Disco disco = existing
                    .map(existingDisco -> mergeDisco(existingDisco, item, pageData.orElse(null), catalogCode, invoice))
                    .orElseGet(() -> buildDisco(item, pageData.orElse(null), catalogCode, invoice));
                disco = discoRepository.save(disco);
                int purchasedQuantity = item.cantidad() != null ? item.cantidad() : 1;
                List<com.sonograma.entity.DiscoQrCopy> copies = existing.isPresent()
                    ? qrCopyService.synchronizeAvailableCopies(disco, availableBefore + purchasedQuantity)
                    : qrCopyService.synchronize(disco);
                qrEntriesCreated += existing.isPresent()
                    ? purchasedQuantity
                    : copies.size();
                Disco savedDisco = disco;
                pageData.ifPresent(page ->
                    audioPreviewService.guardarDesdeTracks(savedDisco.getIdDisco(), page.tracks()));
                imported.add(discoRepository.save(disco));
                if (existing.isPresent() && job != null) {
                    skippedDuplicates++;
                    job.addWarning("Stock actualizado sobre disco existente: " + itemReference(item)
                        + " +" + purchasedQuantity + " copia(s)");
                }
                if (job != null) job.successItem(item);
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
                if (job != null) job.errorItem(item, ex.getMessage());
                log.warn("No se pudo guardar en BD: {} - {}: {}",
                    item.artista(), item.album(), ex.getMessage());
            }
        }

        if (job != null) job.updateStep("Creando códigos QR", 84);
        if (!imported.isEmpty()) {
            try {
                shippingOrderService.crearDesdeImport(imported, items, invoice.total());
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
            invoiceItems.size(),
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
        return new ImportProcessingResult(invoice, summary, searchResults, pageDataMap);
    }

    private Disco buildDisco(InvoiceItem item, VinylPageData page, String catalogCode, ParsedInvoice invoice) {
        String format = page != null ? firstNonBlank(page.format(), item.formato()) : item.formato();
        java.math.BigDecimal cost = item.precioUnitario() != null
            ? item.precioUnitario()
            : (page != null ? page.purchasePrice() : null);
        CatalogPricingService.PricingResult pricing = catalogPricingService.calculate(cost, format);

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
        disco.setCostoMoneda("EUR");
        disco.setFormato(format);
        disco.setNumeroFacturaCompra(invoice.numeroFactura());
        disco.setFechaFacturaCompra(invoice.fechaFactura());
        disco.setPrecioVenta(pricing != null ? pricing.finalPriceUyu() : null);
        disco.setPricingMode(PricingMode.AUTO);
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

    private Optional<Disco> findExistingDisco(InvoiceItem item, VinylPageData page, String catalogCode) {
        if (!blank(catalogCode)) {
            Optional<Disco> byCode = discoRepository.findByCodigoInterno(catalogCode);
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        String artist = firstNonBlank(page != null ? page.artist() : null, item.artista());
        String album = firstNonBlank(page != null ? page.title() : null, item.album());
        if (blank(artist) || blank(album)) {
            return Optional.empty();
        }
        String normalizedFormat = normalize(firstNonBlank(page != null ? page.format() : null, item.formato()));
        String normalizedLabel = normalize(page != null ? page.label() : null);
        return discoRepository.findByArtistaAndAlbumIgnoreCase(artist, album).stream()
            .filter(candidate -> matchesByFallback(candidate, normalizedFormat, normalizedLabel))
            .findFirst();
    }

    private boolean matchesByFallback(Disco candidate, String normalizedFormat, String normalizedLabel) {
        String candidateFormat = normalize(candidate.getFormato());
        String candidateLabel = normalize(candidate.getSelloDiscografico());
        boolean formatMatches = normalizedFormat.isBlank()
            || candidateFormat.isBlank()
            || candidateFormat.equals(normalizedFormat);
        boolean labelMatches = normalizedLabel.isBlank()
            || candidateLabel.isBlank()
            || candidateLabel.equals(normalizedLabel);
        return formatMatches && labelMatches;
    }

    private Disco mergeDisco(Disco disco, InvoiceItem item, VinylPageData page, String catalogCode, ParsedInvoice invoice) {
        String format = firstNonBlank(page != null ? page.format() : null, item.formato(), disco.getFormato());
        java.math.BigDecimal cost = item.precioUnitario() != null
            ? item.precioUnitario()
            : (page != null ? page.purchasePrice() : null);
        disco.setCodigoInterno(firstNonBlank(disco.getCodigoInterno(), catalogCode));
        disco.setArtista(firstNonBlank(disco.getArtista(), page != null ? page.artist() : null, item.artista()));
        disco.setAlbum(firstNonBlank(disco.getAlbum(), page != null ? page.title() : null, item.album()));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setProcedencia(firstNonBlank(disco.getProcedencia(), "VINYL_FUTURE"));
        if (blank(disco.getCodigoQr())) {
            disco.setCodigoQr(UUID.randomUUID().toString());
        }
        if (disco.getTipoDisco() == null) {
            disco.setTipoDisco(parseFormat(format));
        }
        if (disco.getCondicion() == null) {
            disco.setCondicion(parseCondition(page != null ? page.condition() : null));
        }
        disco.setCantidadCopias(Math.max(0, (disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias())
            + (item.cantidad() == null ? 1 : item.cantidad())));
        if (disco.getCosto() == null) {
            disco.setCosto(cost);
        }
        disco.setCostoMoneda(firstNonBlank(disco.getCostoMoneda(), "EUR"));
        disco.setFormato(firstNonBlank(disco.getFormato(), format));
        disco.setNumeroFacturaCompra(invoice.numeroFactura());
        disco.setFechaFacturaCompra(invoice.fechaFactura());
        if (disco.getPrecioVenta() == null && cost != null) {
            CatalogPricingService.PricingResult pricing = catalogPricingService.calculate(cost, format);
            disco.setPrecioVenta(pricing != null ? pricing.finalPriceUyu() : null);
            disco.setPricingMode(PricingMode.AUTO);
        }
        if (page != null) {
            disco.setSelloDiscografico(firstNonBlank(disco.getSelloDiscografico(), page.label()));
            disco.setGenero(firstNonBlank(disco.getGenero(), page.genre()));
            if (disco.getAnio() == null) {
                disco.setAnio(page.year());
            }
            disco.setPais(firstNonBlank(disco.getPais(), page.country()));
            disco.setDescripcion(firstNonBlank(disco.getDescripcion(), page.description()));
            disco.setImagenUrl(firstNonBlank(disco.getImagenUrl(), page.frontImageUrl()));
            disco.setDiscogsUrl(firstNonBlank(disco.getDiscogsUrl(), page.sourceUrl()));
            if (blank(disco.getTracklist()) && !safeTracks(page).isEmpty()) {
                disco.setTracklist(safeTracks(page).stream()
                    .map(track -> firstNonBlank(track.label(), "") + " "
                        + firstNonBlank(track.name(), "Track"))
                    .map(String::strip)
                    .collect(Collectors.joining("\n")));
            }
        }
        return disco;
    }

    private List<InvoiceItem> mergeExactRepeatedRows(List<InvoiceItem> items, VinylFutureJobState job) {
        Map<String, InvoiceItem> merged = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (InvoiceItem item : items) {
            String key = mergeKey(item);
            InvoiceItem existing = merged.get(key);
            if (existing == null) {
                merged.put(key, item);
                counts.put(key, 1);
                continue;
            }
            int quantity = safeQuantity(existing) + safeQuantity(item);
            java.math.BigDecimal subtotal = nvl(existing.subtotal()).add(nvl(item.subtotal()));
            merged.put(key, new InvoiceItem(
                existing.codigoCatalogo(),
                existing.artista(),
                existing.album(),
                existing.formato(),
                existing.precioUnitario(),
                quantity,
                subtotal
            ));
            counts.put(key, counts.getOrDefault(key, 1) + 1);
        }
        counts.forEach((key, count) -> {
            if (count > 1 && job != null) {
                InvoiceItem item = merged.get(key);
                job.addWarning("Fila repetida combinada: " + itemReference(item)
                    + " x" + count + " filas, cantidad total " + item.cantidad());
            }
        });
        return List.copyOf(merged.values());
    }

    private String mergeKey(InvoiceItem item) {
        return normalize(item.codigoCatalogo()) + "|"
            + normalize(item.artista()) + "|"
            + normalize(item.album()) + "|"
            + (item.precioUnitario() == null ? "" : item.precioUnitario().stripTrailingZeros().toPlainString());
    }

    private int safeQuantity(InvoiceItem item) {
        return item.cantidad() == null ? 0 : item.cantidad();
    }

    private java.math.BigDecimal nvl(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    private boolean strongMatch(InvoiceItem item, VinylPageData page, String sourceUrl) {
        String code = normalize(item.codigoCatalogo());
        String pageCode = normalize(page.code());
        String decodedUrl = normalize(sourceUrl == null ? "" : java.net.URLDecoder.decode(sourceUrl, StandardCharsets.UTF_8));
        if (!code.isBlank() && (code.equals(pageCode) || decodedUrl.contains(code))) {
            return true;
        }
        String artist = normalize(item.artista());
        String album = normalize(item.album());
        String pageArtist = normalize(page.artist());
        String pageTitle = normalize(page.title());
        return !artist.isBlank() && !album.isBlank()
            && !pageArtist.isBlank() && !pageTitle.isBlank()
            && (pageArtist.contains(artist) || artist.contains(pageArtist))
            && (pageTitle.contains(album) || album.contains(pageTitle));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
    }

    private String buildZipRootName(ParsedInvoice invoice) {
        String suffix = firstNonBlank(
            sanitizeFilename(invoice.numeroFactura()),
            invoice.fechaFactura() != null ? invoice.fechaFactura().format(DateTimeFormatter.ISO_DATE) : null,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        );
        return "VinylFuture_Invoice_" + suffix;
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "sin-datos";
        }
        String sanitized = value.replaceAll("[/\\\\:*?\"<>|]", "_")
            .replaceAll("\\s+", "_")
            .strip();
        return sanitized.isBlank() ? "sin-datos" : sanitized;
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
        ParsedInvoice invoice,
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

    private class VinylFutureJobState {
        private final String jobId;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private VinylFutureImportJobStatus status = VinylFutureImportJobStatus.PENDING;
        private int progressPercent;
        private String currentStep;
        private String invoiceNumber;
        private java.time.LocalDate invoiceDate;
        private Integer totalItems;
        private Integer totalQuantity;
        private int processedItems;
        private int successCount;
        private int failedCount;
        private int skippedCount;
        private String importId;
        private VinylFutureImportSummaryDTO summary;
        private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        private final List<VinylFutureImportJobItemDTO> items = Collections.synchronizedList(new ArrayList<>());

        private VinylFutureJobState(String jobId) {
            this.jobId = jobId;
        }

        private synchronized void markRunning(String step) {
            status = VinylFutureImportJobStatus.RUNNING;
            startedAt = LocalDateTime.now();
            currentStep = step;
            progressPercent = Math.max(progressPercent, 5);
        }

        private synchronized void updateStep(String step, int progress) {
            currentStep = step;
            progressPercent = Math.max(progressPercent, Math.min(progress, 99));
        }

        private synchronized void setInvoice(ParsedInvoice invoice) {
            invoiceNumber = invoice.numeroFactura();
            invoiceDate = invoice.fechaFactura();
            totalItems = invoice.items().size();
            totalQuantity = invoice.items().stream()
                .map(InvoiceItem::cantidad)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        }

        private synchronized void setItems(List<InvoiceItem> invoiceItems) {
            items.clear();
            for (int index = 0; index < invoiceItems.size(); index++) {
                items.add(VinylFutureImportJobItemDTO.fromInvoiceItem(index + 1, invoiceItems.get(index)));
            }
        }

        private void addWarning(String warning) {
            if (warning != null && !warning.isBlank()) warnings.add(warning);
        }

        private void warnItem(InvoiceItem item, String warning) {
            addWarning(warning);
            updateMatchingItem(item, dto -> dto.addWarning(warning).withStatus("WARNING"));
        }

        private void successItem(InvoiceItem item) {
            processedItems++;
            successCount++;
            updateMatchingItem(item, dto -> dto.withStatus("IMPORTED"));
        }

        private void skipItem(InvoiceItem item, String warning) {
            processedItems++;
            skippedCount++;
            addWarning(warning);
            updateMatchingItem(item, dto -> dto.addWarning(warning).withStatus("SKIPPED"));
        }

        private void errorItem(InvoiceItem item, String error) {
            processedItems++;
            failedCount++;
            String message = error == null || error.isBlank() ? "Error importando producto" : error;
            errors.add(message);
            updateMatchingItem(item, dto -> dto.addError(message));
        }

        private synchronized void complete(VinylFutureImportSummaryDTO completedSummary) {
            summary = completedSummary;
            importId = completedSummary.importId();
            status = errors.isEmpty() && warnings.isEmpty()
                ? VinylFutureImportJobStatus.COMPLETED
                : VinylFutureImportJobStatus.COMPLETED_WITH_ERRORS;
            currentStep = status == VinylFutureImportJobStatus.COMPLETED ? "Completado" : "Completado con errores";
            progressPercent = 100;
            completedAt = LocalDateTime.now();
        }

        private synchronized void fail(String error) {
            String message = error == null || error.isBlank() ? "Falló la importación" : error;
            errors.add(message);
            status = VinylFutureImportJobStatus.FAILED;
            currentStep = "Falló la importación";
            completedAt = LocalDateTime.now();
            progressPercent = Math.max(progressPercent, 100);
        }

        private synchronized VinylFutureImportJobDTO toDto() {
            return new VinylFutureImportJobDTO(
                jobId,
                "VINYL_FUTURE",
                status,
                createdAt,
                startedAt,
                completedAt,
                progressPercent,
                currentStep,
                invoiceNumber,
                invoiceDate,
                totalItems,
                totalQuantity,
                processedItems,
                successCount,
                failedCount,
                skippedCount,
                importId,
                summary,
                List.copyOf(warnings),
                List.copyOf(errors),
                List.copyOf(items)
            );
        }

        private synchronized void updateMatchingItem(
                InvoiceItem item,
                java.util.function.Function<VinylFutureImportJobItemDTO, VinylFutureImportJobItemDTO> updater) {
            for (int index = 0; index < items.size(); index++) {
                VinylFutureImportJobItemDTO dto = items.get(index);
                if (matchesDto(dto, item)) {
                    items.set(index, updater.apply(dto));
                }
            }
        }

        private boolean matchesDto(VinylFutureImportJobItemDTO dto, InvoiceItem item) {
            return normalize(dto.codigoCatalogo()).equals(normalize(item.codigoCatalogo()))
                && normalize(dto.artista()).equals(normalize(item.artista()))
                && normalize(dto.album()).equals(normalize(item.album()))
                && java.util.Objects.equals(dto.unitCostEur(), item.precioUnitario());
        }
    }

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
