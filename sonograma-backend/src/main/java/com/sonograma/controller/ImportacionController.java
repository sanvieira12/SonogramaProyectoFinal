package com.sonograma.controller;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsCoverDownloadDTO;
import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsZipStatusDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO;
import com.sonograma.dto.ManualDiscogsOperationContextDTO;
import com.sonograma.dto.ManualDiscogsOperationDTO;
import com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO;
import com.sonograma.dto.ManualDiscogsFinalizationSnapshotDTO;
import com.sonograma.dto.ManualDiscogsSourceReconciliationDTO;
import com.sonograma.exception.NegocioException;
import com.sonograma.service.importacion.DiscogsImportService;
import com.sonograma.service.importacion.DiscogsImportJobService;
import com.sonograma.service.importacion.DiscogsCoverService;
import com.sonograma.service.importacion.VinylFutureImportService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.DiscogsManualBatchExcelService;
import com.sonograma.service.DiscogsManualBatchZipService;
import com.sonograma.service.DiscogsManualBatchService;
import com.sonograma.service.importacion.ManualDiscogsReceiptOperationService;
import com.sonograma.service.ManualDiscogsSourceReconciliationService;
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
import java.util.UUID;

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
    private final DiscogsManualBatchExcelService discogsManualBatchExcelService;
    private final DiscogsManualBatchZipService discogsManualBatchZipService;
    private final DiscogsManualBatchService discogsManualBatchService;
    private final ManualDiscogsReceiptOperationService manualDiscogsReceiptOperationService;
    private final ManualDiscogsSourceReconciliationService manualDiscogsSourceReconciliationService;

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
        DiscoImportPreviewDTO preview = discogsImportService.fetchDesdeLink(url);
        String source = body.get("sourceCustomerCode");
        if (preview.getOperationId() != null && source != null && !source.isBlank()) {
            manualDiscogsReceiptOperationService.updateContext(
                    UUID.fromString(preview.getOperationId()),
                    new ManualDiscogsOperationContextDTO(source, null, null));
            preview.setCustomerCode(source.trim());
        }
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/discogs/guardar")
    public ResponseEntity<ManualDiscogsImportResultDTO> discogsGuardar(
            @RequestBody DiscoImportPreviewDTO preview) {
        return ResponseEntity.ok(discogsImportService.guardar(preview));
    }

    @PostMapping("/discogs/manual/cover")
    public ResponseEntity<DiscogsCoverDownloadDTO> discogsManualCover(
            @RequestBody DiscoImportPreviewDTO preview) {
        return ResponseEntity.ok(discogsImportService.descargarPortada(preview));
    }

    @PostMapping("/discogs/manual/zip")
    public ResponseEntity<StreamingResponseBody> downloadDiscogsManualZip(
            @RequestBody DiscoImportPreviewDTO preview) {
        String releaseId = preview == null || preview.getDiscogsReleaseId() == null
                ? "release" : preview.getDiscogsReleaseId().toString();
        StreamingResponseBody body = output -> discogsImportService.escribirZip(preview, output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"discogs-release-" + releaseId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @GetMapping("/discogs/manual-operations/pending")
    public ResponseEntity<List<ManualDiscogsOperationDTO>> pendingManualDiscogsOperations(
            @RequestParam("source") String source) {
        return ResponseEntity.ok(manualDiscogsReceiptOperationService.listPending(source));
    }

    @GetMapping("/discogs/manual-operations/{operationId}")
    public ResponseEntity<ManualDiscogsOperationDTO> manualDiscogsOperation(
            @PathVariable UUID operationId) {
        return ResponseEntity.ok(manualDiscogsReceiptOperationService.get(operationId));
    }

    @PatchMapping("/discogs/manual-operations/{operationId}/context")
    public ResponseEntity<ManualDiscogsOperationDTO> updateManualDiscogsOperationContext(
            @PathVariable UUID operationId,
            @RequestBody ManualDiscogsOperationContextDTO context) {
        return ResponseEntity.ok(manualDiscogsReceiptOperationService.updateContext(operationId, context));
    }

    @PostMapping("/discogs/manual-operations/{operationId}/abandon")
    public ResponseEntity<ManualDiscogsOperationDTO> abandonManualDiscogsOperation(
            @PathVariable UUID operationId) {
        return ResponseEntity.ok(manualDiscogsReceiptOperationService.abandon(operationId));
    }

    @GetMapping("/discogs/manual-sources/{source}/reconciliation")
    public ResponseEntity<ManualDiscogsSourceReconciliationDTO> manualDiscogsSourceReconciliation(
            @PathVariable String source) {
        return ResponseEntity.ok(manualDiscogsSourceReconciliationService.current(source));
    }

    @PutMapping("/discogs/manual-sources/{source}/reconciliation/expected-count")
    public ResponseEntity<ManualDiscogsSourceReconciliationDTO> updateManualDiscogsExpectedCount(
            @PathVariable String source,
            @RequestBody ManualDiscogsExpectedCountRequestDTO request) {
        return ResponseEntity.ok(manualDiscogsSourceReconciliationService.updateExpectedCount(source, request));
    }

    @GetMapping("/discogs/manual-sources/{source}/reconciliation/snapshots")
    public ResponseEntity<List<ManualDiscogsFinalizationSnapshotDTO>> manualDiscogsFinalizationSnapshots(
            @PathVariable String source) {
        return ResponseEntity.ok(manualDiscogsSourceReconciliationService.snapshots(source));
    }

    @GetMapping("/discogs/manual-sources/{source}/excel")
    public ResponseEntity<byte[]> downloadDiscogsManualSourceExcel(@PathVariable String source) {
        DiscogsManualBatchExcelService.GeneratedWorkbook workbook =
                discogsManualBatchExcelService.generateForSource(source);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + workbook.filename() + "\"")
                .contentType(MediaType.parseMediaType(DiscogsManualBatchExcelService.XLSX_MEDIA_TYPE))
                .contentLength(workbook.content().length)
                .body(workbook.content());
    }

    @GetMapping("/discogs/manual-batches/{batchId}/excel")
    public ResponseEntity<byte[]> downloadDiscogsManualBatchExcel(@PathVariable Long batchId) {
        DiscogsManualBatchExcelService.GeneratedWorkbook workbook =
                discogsManualBatchExcelService.generate(batchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + workbook.filename() + "\"")
                .contentType(MediaType.parseMediaType(DiscogsManualBatchExcelService.XLSX_MEDIA_TYPE))
                .contentLength(workbook.content().length)
                .body(workbook.content());
    }

    @GetMapping("/discogs/manual-batches/{batchId}/zip")
    public ResponseEntity<byte[]> downloadDiscogsManualBatchZip(@PathVariable Long batchId) {
        DiscogsManualBatchZipService.GeneratedZip zip = discogsManualBatchZipService.generate(batchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + zip.filename() + "\"")
                .contentType(MediaType.parseMediaType(DiscogsManualBatchZipService.ZIP_MEDIA_TYPE))
                .contentLength(zip.content().length)
                .body(zip.content());
    }

    @PostMapping("/discogs/manual-batches/{batchId}/finalize")
    public ResponseEntity<DiscogsManualBatchService.FinalizedBatch> finalizeDiscogsManualBatch(
            @PathVariable Long batchId,
            @RequestBody DiscogsManualBatchFinalizeRequestDTO request) {
        return ResponseEntity.ok(discogsManualBatchService.finalizeBatch(batchId, request));
    }

    // ── Discogs — Excel con links ─────────────────────────────────────────────

    @PostMapping({"/discogs/jobs", "/discogs/desde-excel"})
    public ResponseEntity<DiscogsImportJobDTO> discogsDesdeExcel(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("El archivo Excel está vacío");
        }
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

    @PostMapping("/discogs/jobs/{jobId}/retry-pending")
    public ResponseEntity<DiscogsImportJobDTO> discogsRetryPending(@PathVariable Long jobId) {
        return ResponseEntity.ok(discogsImportJobService.retryPendingRows(jobId));
    }

    @PostMapping("/discogs/jobs/{jobId}/resume")
    public ResponseEntity<DiscogsImportJobDTO> discogsResume(@PathVariable Long jobId) {
        return ResponseEntity.accepted().body(discogsImportJobService.resumeJob(jobId));
    }

    @PostMapping("/discogs/jobs/{jobId}/importar")
    public ResponseEntity<DiscogsImportJobDTO> discogsImportar(@PathVariable Long jobId) {
        return ResponseEntity.ok(discogsImportJobService.importParsedRows(jobId));
    }

    @PostMapping("/discogs/jobs/{jobId}/covers-zip")
    public ResponseEntity<DiscogsZipStatusDTO> prepareDiscogsCoversZip(@PathVariable Long jobId) {
        return ResponseEntity.accepted().body(discogsImportJobService.prepareCoversZip(jobId));
    }

    @GetMapping("/discogs/jobs/{jobId}/covers-zip/status")
    public ResponseEntity<DiscogsZipStatusDTO> discogsCoversZipStatus(@PathVariable Long jobId) {
        return ResponseEntity.ok(discogsImportJobService.getCoversZipStatus(jobId));
    }

    @GetMapping({
            "/discogs/jobs/{jobId}/covers-zip/download",
            "/discogs/jobs/{jobId}/covers.zip"
    })
    public ResponseEntity<StreamingResponseBody> downloadDiscogsCoversZip(@PathVariable Long jobId)
            throws IOException {
        Path zip = discogsImportJobService.getPreparedCoversZip(jobId);
        long size = Files.size(zip);
        StreamingResponseBody body = output -> Files.copy(zip, output);
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
