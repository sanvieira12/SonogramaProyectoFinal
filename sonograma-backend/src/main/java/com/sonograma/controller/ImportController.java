package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.VinylPageData;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Parses a deejay.de invoice PDF, searches each item on vinylfuture.com,
     * then scrapes the product page for cover images and MP3 preview tracks.
     * Returns a ZIP containing import.csv plus one folder per album with images/ and audio/.
     */
    @PostMapping("/vinylfuture-csv")
    public ResponseEntity<byte[]> procesarFactura(@RequestParam MultipartFile file) {
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

        // 3. Build CSV + ZIP
        String csv = csvExport.buildCsv(searchResults);

        byte[] zip;
        try {
            zip = zipBundle.buildZip(csv, pageDataMap);
        } catch (Exception e) {
            log.error("Error al construir el ZIP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(("Error al generar el archivo: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "vinylfuture-import-" + timestamp + ".zip";

        log.info("ZIP generado: {} bytes.", zip.length);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
    }
}
