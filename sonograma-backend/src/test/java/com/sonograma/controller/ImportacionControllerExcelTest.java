package com.sonograma.controller;

import com.sonograma.service.DiscogsManualBatchExcelService;
import com.sonograma.service.DiscogsManualBatchZipService;
import com.sonograma.service.DiscogsManualBatchService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.importacion.DiscogsCoverService;
import com.sonograma.service.importacion.DiscogsImportJobService;
import com.sonograma.service.importacion.DiscogsImportService;
import com.sonograma.service.importacion.VinylFutureImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacionControllerExcelTest {

    @Mock private VinylFutureImportService vinylFutureImportService;
    @Mock private DiscogsImportService discogsImportService;
    @Mock private DiscogsImportJobService discogsImportJobService;
    @Mock private DiscogsCoverService discogsCoverService;
    @Mock private VinylFutureAssetService vinylFutureAssetService;
    @Mock private DiscogsManualBatchExcelService excelService;
    @Mock private DiscogsManualBatchZipService zipService;
    @Mock private DiscogsManualBatchService batchService;

    @Test
    void returnsGeneratedWorkbookAsXlsxAttachment() {
        byte[] content = "xlsx-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(excelService.generate(15L)).thenReturn(
                new DiscogsManualBatchExcelService.GeneratedWorkbook(content, "JPH_2026-09-04_batch-15.xlsx"));

        ResponseEntity<byte[]> response = controller().downloadDiscogsManualBatchExcel(15L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo(DiscogsManualBatchExcelService.XLSX_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"JPH_2026-09-04_batch-15.xlsx\"");
    }

    @Test
    void returnsGeneratedBatchZipAsAttachment() {
        byte[] content = "zip-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(zipService.generate(15L)).thenReturn(
                new DiscogsManualBatchZipService.GeneratedZip(content, "JPH_2026-09-04_batch-15.zip"));

        ResponseEntity<byte[]> response = controller().downloadDiscogsManualBatchZip(15L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo(DiscogsManualBatchZipService.ZIP_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"JPH_2026-09-04_batch-15.zip\"");
    }

    @Test
    void finalizesManualBatchThroughLifecycleEndpoint() {
        LocalDateTime finalizedAt = LocalDateTime.of(2026, 9, 4, 12, 0);
        when(batchService.finalizeBatch(15L)).thenReturn(new DiscogsManualBatchService.FinalizedBatch(
                15L, com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED, finalizedAt));

        ResponseEntity<DiscogsManualBatchService.FinalizedBatch> response =
                controller().finalizeDiscogsManualBatch(15L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isEqualTo(15L);
        assertThat(response.getBody().status())
                .isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED);
        assertThat(response.getBody().finalizedAt()).isEqualTo(finalizedAt);
    }

    private ImportacionController controller() {
        return new ImportacionController(
                vinylFutureImportService,
                discogsImportService,
                discogsImportJobService,
                discogsCoverService,
                vinylFutureAssetService,
                excelService,
                zipService,
                batchService);
    }
}
