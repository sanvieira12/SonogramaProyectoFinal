package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.service.CsvExportService;
import com.sonograma.service.PdfInvoiceParser;
import com.sonograma.service.VinylFutureSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
    private final CsvExportService csvExport;

    @PostMapping("/vinylfuture-csv")
    public ResponseEntity<byte[]> procesarFactura(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body("Archivo vacío".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        log.info("Recibido PDF '{}' ({} bytes). Iniciando procesamiento.", file.getOriginalFilename(), file.getSize());

        List<InvoiceItem> items;
        try {
            items = pdfParser.parse(file);
        } catch (IOException e) {
            log.warn("No se pudo parsear el PDF: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(("No se pudo leer el PDF: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (items.isEmpty()) {
            log.warn("No se extrajeron ítems del PDF.");
        }

        // Search each item on vinylfuture (preserving insertion order for output)
        Map<InvoiceItem, Optional<String>> results = new LinkedHashMap<>();
        for (InvoiceItem item : items) {
            Optional<String> url = vinylFutureSearch.buscar(item);
            results.put(item, url);
        }

        long found = results.values().stream().filter(Optional::isPresent).count();
        log.info("Búsqueda completada. {}/{} ítems encontrados en vinylfuture.", found, items.size());

        String csv = csvExport.buildCsv(results);
        byte[] csvBytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "vinylfuture-import-" + timestamp + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csvBytes);
    }
}
