package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.CsvExportService;
import com.sonograma.service.PdfInvoiceParser;
import com.sonograma.service.VinylFutureScraperService;
import com.sonograma.service.VinylFutureSearchService;
import com.sonograma.service.ZipBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Parses a deejay.de invoice PDF, searches each item on vinylfuture.com,
     * then scrapes the product page for cover images and MP3 preview tracks.
     * Returns a ZIP containing import.csv plus one folder per album with images/ and audio/.
     */
    @PostMapping("/vinylfuture-csv")
    @Transactional
    public ResponseEntity<?> procesarFactura(@RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body("Archivo vacío".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        log.info("Recibido PDF '{}' ({} bytes). Iniciando procesamiento.", file.getOriginalFilename(), file.getSize());

        // Read bytes once — reused for both text parsing and link extraction
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                .body(("No se pudo leer el PDF: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        List<InvoiceItem> items;
        List<String> pdfLinks;
        try {
            items    = pdfParser.parse(pdfBytes);
            pdfLinks = pdfParser.extractLinks(pdfBytes);
        } catch (IOException e) {
            log.warn("No se pudo parsear el PDF: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(("No se pudo leer el PDF: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (items.isEmpty()) {
            log.warn("No se extrajeron ítems del PDF.");
        }

        // 1. Search each item on vinylfuture (for CSV url_vinylfuture column)
        Map<InvoiceItem, Optional<String>> searchResults = new LinkedHashMap<>();
        for (InvoiceItem item : items) {
            searchResults.put(item, vinylFutureSearch.buscar(item));
        }

        long found = searchResults.values().stream().filter(Optional::isPresent).count();
        log.info("Búsqueda completada. {}/{} ítems encontrados en vinylfuture.", found, items.size());

        // 2. Scrape images and MP3s from each VinylFuture product page
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap = new LinkedHashMap<>();
        for (Map.Entry<InvoiceItem, Optional<String>> entry : searchResults.entrySet()) {
            Optional<VinylPageData> pageData = entry.getValue()
                .flatMap(url -> vinylFutureScraper.scrape(url));
            pageDataMap.put(entry.getKey(), pageData);
        }

        // 3. Persist to DB
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
                disco.setCantidadCopias(1);
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
                discoRepository.save(disco);
                log.info("Disco guardado en BD: {} - {}", item.artista(), item.album());
            } catch (Exception e) {
                log.warn("No se pudo guardar en BD: {} - {}: {}", item.artista(), item.album(), e.getMessage());
            }
        }

        // 4. Build CSV + ZIP
        String csv = csvExport.buildCsv(searchResults);

        Path zipPath;
        try {
            zipPath = zipBundle.buildZip(csv, pageDataMap);
        } catch (Exception e) {
            log.error("Error al construir el ZIP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(("Error al generar el archivo: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "vinylfuture-import-" + timestamp + ".zip";

        try {
            log.info("ZIP generado: {} bytes.", Files.size(zipPath));
        } catch (IOException e) {
            log.warn("No se pudo determinar el tamaño del ZIP: {}", e.getMessage());
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
            .body(body);
    }
}
