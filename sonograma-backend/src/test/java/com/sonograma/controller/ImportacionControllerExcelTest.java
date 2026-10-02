package com.sonograma.controller;

import com.sonograma.service.DiscogsManualBatchExcelService;
import com.sonograma.service.DiscogsManualBatchZipService;
import com.sonograma.service.DiscogsManualBatchService;
import com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO;
import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.ManualDiscogsOperationContextDTO;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.importacion.DiscogsCoverService;
import com.sonograma.service.importacion.DiscogsImportJobService;
import com.sonograma.service.importacion.DiscogsImportService;
import com.sonograma.service.importacion.VinylFutureImportService;
import com.sonograma.service.importacion.ManualDiscogsReceiptOperationService;
import com.sonograma.service.ManualDiscogsSourceReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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
    @Mock private ManualDiscogsReceiptOperationService operationService;
    @Mock private ManualDiscogsSourceReconciliationService reconciliationService;

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
    void returnsLogicalSourceWorkbookWithoutABatchFilename() {
        byte[] content = "source-xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(excelService.generateForSource("SV3")).thenReturn(
                new DiscogsManualBatchExcelService.GeneratedWorkbook(content, "SV3_2026-09-30.xlsx"));

        ResponseEntity<byte[]> response = controller().downloadDiscogsManualSourceExcel("SV3");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"SV3_2026-09-30.xlsx\"");
        verify(excelService).generateForSource("SV3");
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
        when(batchService.finalizeBatch(15L, new DiscogsManualBatchFinalizeRequestDTO(30))).thenReturn(new DiscogsManualBatchService.FinalizedBatch(
                15L, com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED, finalizedAt, 30, 91L));

        ResponseEntity<DiscogsManualBatchService.FinalizedBatch> response =
                controller().finalizeDiscogsManualBatch(15L, new DiscogsManualBatchFinalizeRequestDTO(30));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isEqualTo(15L);
        assertThat(response.getBody().status())
                .isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED);
        assertThat(response.getBody().finalizedAt()).isEqualTo(finalizedAt);
        assertThat(response.getBody().porcentajeSonograma()).isEqualTo(30);
    }

    @Test
    void lookupPersistsSourceAsSoonAsThePendingOperationExists() {
        String operationId = "0f8fad5b-d9cb-469f-a165-70867728950e";
        DiscoImportPreviewDTO preview = DiscoImportPreviewDTO.builder()
                .operationId(operationId)
                .discogsReleaseId(456L)
                .build();
        when(discogsImportService.fetchDesdeLink("https://www.discogs.com/release/456"))
                .thenReturn(preview);

        ResponseEntity<DiscoImportPreviewDTO> response = controller().discogsDesdeLink(java.util.Map.of(
                "url", "https://www.discogs.com/release/456",
                "sourceCustomerCode", " sourcea "));

        assertThat(response.getBody().getCustomerCode()).isEqualTo("sourcea");
        verify(operationService).updateContext(
                java.util.UUID.fromString(operationId),
                new ManualDiscogsOperationContextDTO(" sourcea ", null, null));
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
                batchService,
                operationService,
                reconciliationService);
    }
}
