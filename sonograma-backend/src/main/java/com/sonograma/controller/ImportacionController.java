package com.sonograma.controller;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.service.importacion.DiscogsImportService;
import com.sonograma.service.importacion.VinylFutureImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/importaciones")
@RequiredArgsConstructor
@Slf4j
public class ImportacionController {

    private final VinylFutureImportService vinylFutureImportService;
    private final DiscogsImportService discogsImportService;

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

    @PostMapping("/discogs/desde-excel")
    public ResponseEntity<List<DiscoImportPreviewDTO>> discogsDesdeExcel(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            List<DiscoImportPreviewDTO> resultados = discogsImportService.fetchDesdeExcel(file);
            return ResponseEntity.ok(resultados);
        } catch (IOException e) {
            log.warn("Error parseando Excel Discogs: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/discogs/guardar-lote")
    public ResponseEntity<List<DiscoResponseDTO>> discogsGuardarLote(
            @RequestBody List<DiscoImportPreviewDTO> previews) {
        return ResponseEntity.ok(discogsImportService.guardarLote(previews));
    }
}
