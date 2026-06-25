package com.sonograma.controller;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.service.importacion.DiscogsImportService;
import com.sonograma.service.importacion.DiscogsImportJobService;
import com.sonograma.service.importacion.DiscogsCoverService;
import com.sonograma.service.importacion.VinylFutureImportService;
import com.sonograma.service.VinylFutureAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/importaciones")
@RequiredArgsConstructor
@Slf4j
public class ImportacionController {

    private final VinylFutureImportService vinylFutureImportService;
    private final DiscogsImportService discogsImportService;
    private final DiscogsImportJobService discogsImportJobService;
    private final DiscogsCoverService discogsCoverService;
    private final VinylFutureAssetService vinylFutureAssetService;

    // ── VinylFuture Excel ─────────────────────────────────────────────────────

    @PostMapping("/vinylfuture/preview")
    public ResponseEntity<List<DiscoImportPreviewDTO>> vinylfuturePreview(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            List<DiscoImportPreviewDTO> preview = vinylFutureImportService.parsearExcel(file);
            return ResponseEntity.ok(preview);
        } catch (IOException e) {
            log.warn("Error parseando Excel VinylFuture: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/vinylfuture/confirmar")
    public ResponseEntity<List<DiscoResponseDTO>> vinylfutureConfirmar(
            @RequestBody List<DiscoImportPreviewDTO> seleccionados) {
        List<DiscoResponseDTO> guardados = vinylFutureImportService.confirmarImport(seleccionados);
        return ResponseEntity.ok(guardados);
    }

    @GetMapping("/vinylfuture/media/{*filename}")
    public ResponseEntity<Resource> vinylfutureMedia(@PathVariable String filename) throws IOException {
        filename = decodeMediaPath(filename);
        Resource resource = vinylFutureAssetService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(vinylFutureAssetService.contentType(filename)))
                .body(resource);
    }

    private String decodeMediaPath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    // ── Discogs — link único ──────────────────────────────────────────────────

    @PostMapping("/discogs/desde-link")
    public ResponseEntity<DiscoImportPreviewDTO> discogsDesdeLink(
            @RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(discogsImportService.fetchDesdeLink(url));
    }

    @PostMapping("/discogs/guardar")
    public ResponseEntity<DiscoResponseDTO> discogsGuardar(
            @RequestBody DiscoImportPreviewDTO preview) {
        return ResponseEntity.ok(discogsImportService.guardar(preview));
    }

    // ── Discogs — Excel con links ─────────────────────────────────────────────

    @PostMapping({"/discogs/jobs", "/discogs/desde-excel"})
    public ResponseEntity<DiscogsImportJobDTO> discogsDesdeExcel(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(discogsImportJobService.createJob(file));
    }

    @GetMapping("/discogs/jobs/{jobId}")
    public ResponseEntity<DiscogsImportJobDTO> discogsJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(discogsImportJobService.getJob(jobId));
    }

    @PostMapping("/discogs/jobs/{jobId}/rows/{rowId}/retry")
    public ResponseEntity<DiscogsImportJobDTO> discogsRetryRow(
            @PathVariable Long jobId,
            @PathVariable Long rowId) {
        return ResponseEntity.ok(discogsImportJobService.retryRow(jobId, rowId));
    }

    @PostMapping("/discogs/jobs/{jobId}/importar")
    public ResponseEntity<DiscogsImportJobDTO> discogsImportar(@PathVariable Long jobId) {
        return ResponseEntity.ok(discogsImportJobService.importParsedRows(jobId));
    }

    @GetMapping("/discogs/jobs/{jobId}/covers.zip")
    public ResponseEntity<StreamingResponseBody> discogsCoversZip(@PathVariable Long jobId) throws IOException {
        Path zip = discogsImportJobService.buildCoversZip(jobId);
        long size = Files.size(zip);
        StreamingResponseBody body = output -> {
            try {
                Files.copy(zip, output);
            } finally {
                Files.deleteIfExists(zip);
            }
        };
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"discogs-covers-" + timestamp + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(size)
                .body(body);
    }

    @GetMapping("/discogs/covers/{filename:.+}")
    public ResponseEntity<Resource> discogsCover(@PathVariable String filename) throws IOException {
        Resource resource = discogsCoverService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(discogsCoverService.contentType(filename)))
                .body(resource);
    }

    @PostMapping("/discogs/guardar-lote")
    public ResponseEntity<List<DiscoResponseDTO>> discogsGuardarLote(
            @RequestBody List<DiscoImportPreviewDTO> previews) {
        return ResponseEntity.ok(discogsImportService.guardarLote(previews));
    }
}
