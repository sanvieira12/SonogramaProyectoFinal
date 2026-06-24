package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
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
import com.sonograma.service.ZipBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final CsvExportService csvExport;
    private final ZipBundleService zipBundle;
    private final DiscoRepository discoRepository;
    private final ShippingOrderService shippingOrderService;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final CatalogPricingService catalogPricingService;
    private final ExecutorService importPool = Executors.newFixedThreadPool(4);

    /**
     * Primary PDF flow: imports complete records into the catalog without forcing a download.
     */
    @PostMapping("/vinylfuture-catalogo")
    @Transactional
    public ResponseEntity<VinylFutureImportSummaryDTO> importarFacturaAlCatalogo(
            @RequestParam MultipartFile file) {
        try {
            return ResponseEntity.ok(processImport(file).summary());
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

        String csv = csvExport.buildCsv(result.searchResults());

        Path zipPath;
        try {
            zipPath = zipBundle.buildZip(csv, result.pageDataMap());
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
            item -> searchResults.getOrDefault(item, Optional.empty()).flatMap(vinylFutureScraper::scrape)
        );
        log.info("Scraping completado en {} ms.", elapsedMillis(scrapeStarted));

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
                int mp3Count = pageData.map(page -> (int) page.tracks().stream()
                    .filter(track -> !blank(track.mp3Url()))
                    .count()).orElse(0);
                int youtubeCount = pageData.map(page -> (int) page.tracks().stream()
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

        int coversFound = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .filter(page -> !blank(page.frontImageUrl()))
            .count();
        int mp3Found = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .flatMap(page -> page.tracks().stream())
            .filter(track -> !blank(track.mp3Url()))
            .count();
        int youtubeFound = (int) pageDataMap.values().stream()
            .flatMap(Optional::stream)
            .flatMap(page -> page.tracks().stream())
            .filter(track -> !blank(track.youtubeUrl()))
            .count();
        List<String> failedLinks = items.stream()
            .filter(item -> searchResults.getOrDefault(item, Optional.empty()).isEmpty()
                || pageDataMap.getOrDefault(item, Optional.empty()).isEmpty())
            .map(item -> firstNonBlank(
                item.codigoCatalogo(), item.artista() + " - " + item.album()))
            .distinct()
            .toList();

        VinylFutureImportSummaryDTO summary = new VinylFutureImportSummaryDTO(
            items.size(),
            imported.size(),
            coversFound,
            mp3Found,
            youtubeFound,
            qrEntriesCreated,
            failedLinks.size(),
            skippedDuplicates,
            0,
            failedLinks
        );
        log.info(
            "Resumen Vinyl Future: detectados={}, importados={}, portadas={}, mp3={}, youtube={}, "
                + "qr={}, fallidos={}, duplicados={}, rateLimit={}",
            summary.recordsDetected(), summary.recordsImported(), summary.coversFound(),
            summary.mp3PreviewsFound(), summary.youtubeLinksFound(), summary.qrEntriesCreated(),
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
            if (page.tracks() != null && !page.tracks().isEmpty()) {
                disco.setTracklist(page.tracks().stream()
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

    private record ImportProcessingResult(
        VinylFutureImportSummaryDTO summary,
        Map<InvoiceItem, Optional<String>> searchResults,
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap
    ) {}

    private <T> Map<InvoiceItem, T> parallelMap(
            List<InvoiceItem> items,
            Function<InvoiceItem, T> operation) {
        Map<InvoiceItem, CompletableFuture<T>> futures = items.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                item -> CompletableFuture.supplyAsync(() -> operation.apply(item), importPool),
                (first, ignored) -> first,
                LinkedHashMap::new
            ));

        Map<InvoiceItem, T> results = new LinkedHashMap<>();
        futures.forEach((item, future) -> results.put(item, future.join()));
        return results;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
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
}
