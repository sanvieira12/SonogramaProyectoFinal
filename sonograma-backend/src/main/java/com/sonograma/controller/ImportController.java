package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.CsvExportService;
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
    private final ExecutorService importPool = Executors.newFixedThreadPool(4);

    /**
     * Parses a deejay.de invoice PDF, searches each item on vinylfuture.com,
     * then scrapes the product page for cover images and MP3 preview tracks.
     * Returns a ZIP containing import.csv plus one folder per album with images/ and audio/.
     */
    @PostMapping("/vinylfuture-csv")
    @Transactional
    public ResponseEntity<StreamingResponseBody> procesarFactura(@RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Archivo vacío");
        }

        log.info("Recibido PDF '{}' ({} bytes). Iniciando procesamiento.", file.getOriginalFilename(), file.getSize());

        // Read bytes once — reused for both text parsing and link extraction
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + e.getMessage());
        }

        ParsedInvoice invoice;
        try {
            invoice = pdfParser.parseInvoice(pdfBytes);
        } catch (IOException e) {
            log.warn("No se pudo parsear el PDF: {}", e.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo leer el PDF: " + e.getMessage());
        }

        List<InvoiceItem> items = invoice.items();
        if (items.isEmpty()) {
            log.warn("No se extrajeron ítems del PDF.");
        }

        long searchStarted = System.nanoTime();
        Map<InvoiceItem, Optional<String>> searchResults = parallelMap(
            items,
            vinylFutureSearch::buscar
        );

        long found = searchResults.values().stream().filter(Optional::isPresent).count();
        log.info("Búsqueda completada en {} ms. {}/{} ítems encontrados en vinylfuture.",
            elapsedMillis(searchStarted), found, items.size());

        long scrapeStarted = System.nanoTime();
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap = parallelMap(
            items,
            item -> searchResults.getOrDefault(item, Optional.empty())
                .flatMap(vinylFutureScraper::scrape)
        );
        log.info("Scraping completado en {} ms.", elapsedMillis(scrapeStarted));

        // 3. Persist to DB
        List<Disco> discosGuardados = new ArrayList<>();
        for (Map.Entry<InvoiceItem, Optional<VinylPageData>> entry : pageDataMap.entrySet()) {
            InvoiceItem item = entry.getKey();
            Optional<VinylPageData> pageData = entry.getValue();
            try {
                if (item.codigoCatalogo() != null
                        && !item.codigoCatalogo().isBlank()
                        && discoRepository.findByCodigoInterno(item.codigoCatalogo()).isPresent()) {
                    log.info("Disco ya importado, se omite: {} / {}", item.codigoCatalogo(), item.album());
                    continue;
                }

                Disco disco = new Disco();
                disco.setArtista(item.artista());
                disco.setAlbum(item.album());
                disco.setCodigoInterno(item.codigoCatalogo());
                disco.setEstado(EstadoDisco.DISPONIBLE);
                disco.setCodigoQr(UUID.randomUUID().toString());
                disco.setProcedencia("IMPORTADO");
                disco.setTipoDisco(TipoDisco.VINILO);
                disco.setCondicion(CondicionDisco.NUEVO);
                disco.setCantidadCopias(item.cantidad() != null ? item.cantidad() : 1);
                disco.setCosto(item.precioUnitario());
                if (pageData.isPresent()) {
                    VinylPageData page = pageData.get();
                    disco.setImagenUrl(page.frontImageUrl());
                    if (!page.tracks().isEmpty()) {
                        String tracklist = page.tracks().stream()
                            .map(t -> t.label() + ". " + t.name())
                            .collect(java.util.stream.Collectors.joining("\n"));
                        disco.setTracklist(tracklist);
                    }
                }
                discosGuardados.add(discoRepository.save(disco));
                log.info("Disco guardado en BD: {} - {}", item.artista(), item.album());
            } catch (Exception e) {
                log.warn("No se pudo guardar en BD: {} - {}: {}", item.artista(), item.album(), e.getMessage());
            }
        }

        // 3b. Auto-create ShippingOrder for imported discos
        if (!discosGuardados.isEmpty()) {
            try {
                shippingOrderService.crearDesdeImport(
                    discosGuardados,
                    invoice.items(),
                    invoice.total()
                );
                log.info("ShippingOrder creada con {} ítems.", discosGuardados.size());
            } catch (Exception e) {
                log.warn("No se pudo crear ShippingOrder: {}", e.getMessage());
            }
        }

        // 4. Build CSV + ZIP
        String csv = csvExport.buildCsv(searchResults);

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
