package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylFutureImportJobDTO;
import com.sonograma.dto.VinylFutureImportJobStartDTO;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.VinylFutureImportJobStatus;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.CatalogPricingService;
import com.sonograma.service.CsvExportService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.PdfInvoiceParser;
import com.sonograma.service.ShippingOrderService;
import com.sonograma.service.VinylFutureScraperService;
import com.sonograma.service.VinylFutureSearchService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.VinylFutureImportBatchService;
import com.sonograma.service.ZipBundleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    @Mock private PdfInvoiceParser pdfParser;
    @Mock private VinylFutureSearchService searchService;
    @Mock private VinylFutureScraperService scraperService;
    @Mock private VinylFutureAssetService assetService;
    @Mock private CsvExportService csvExportService;
    @Mock private ZipBundleService zipBundleService;
    @Mock private DiscoRepository discoRepository;
    @Mock private ShippingOrderService shippingOrderService;
    @Mock private AudioPreviewService audioPreviewService;
    @Mock private DiscoQrCopyService qrCopyService;
    @Mock private CatalogPricingService pricingService;
    @Mock private VinylFutureImportBatchService importBatchService;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void catalogImportPersistsStructuredDataWithoutBuildingZip() throws Exception {
        InvoiceItem item = new InvoiceItem(
            "CAT-123", "Invoice Artist", "Invoice Album", "2x12",
            new BigDecimal("12.00"), 2, new BigDecimal("24.00")
        );
        ParsedInvoice invoice = new ParsedInvoice(
            List.of(item), List.of(), new BigDecimal("24.00"), 2,
            null, null, null, "0031-188471", null, null, null, null, null, null, null, null, null, null, null, null
        );
        VinylPageData page = new VinylPageData(
            "https://www.vinylfuture.com/release_Vinyl__123",
            "Scraped Artist", "Scraped Album", "CAT-123", "Test Label", "House", 2025,
            "Germany", "2x12", "New", "Full metadata", new BigDecimal("13.00"),
            "https://cdn.example/cover.jpg", null,
            List.of(new TrackInfo("A1", "First Track", "https://cdn.example/a1.mp3", null))
        );
        VinylPageData storedPage = new VinylPageData(
            page.sourceUrl(),
            page.artist(), page.title(), page.code(), page.label(), page.genre(), page.year(),
            page.country(), page.format(), page.condition(), page.description(), page.purchasePrice(),
            "/api/importar/vinylfuture/media/CAT-123/cover.jpg", null,
            List.of(new TrackInfo("A1", "First Track", "/api/importar/vinylfuture/media/CAT-123/a1.mp3", null))
        );

        when(pdfParser.parseInvoice(any(byte[].class))).thenReturn(invoice);
        when(discoRepository.existsByNumeroFacturaCompra("0031-188471")).thenReturn(false);
        when(searchService.buscar(item)).thenReturn(Optional.of(page.sourceUrl()));
        when(scraperService.scrape(page.sourceUrl())).thenReturn(Optional.of(page));
        when(assetService.storeAssetsWithResult(item, page))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(storedPage, 1, 1, 0));
        when(discoRepository.findByCodigoInterno("CAT-123")).thenReturn(Optional.empty());
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> {
            Disco disco = invocation.getArgument(0);
            disco.setIdDisco(10L);
            return disco;
        });
        when(qrCopyService.synchronize(any(Disco.class))).thenReturn(List.of(
            DiscoQrCopy.builder().idDisco(10L).copyNumber(1).codigoQr("qr-1").build(),
            DiscoQrCopy.builder().idDisco(10L).copyNumber(2).codigoQr("qr-2").build()
        ));
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        when(importBatchService.store(any(), any(), anyString())).thenReturn("import-123");
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ImportController controller = new ImportController(
            pdfParser,
            searchService,
            scraperService,
            assetService,
            csvExportService,
            zipBundleService,
            discoRepository,
            shippingOrderService,
            audioPreviewService,
            qrCopyService,
            pricingService,
            importBatchService,
            transactionManager
        );
        MockMultipartFile file = new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf", "pdf".getBytes()
        );
        ResponseEntity<VinylFutureImportJobStartDTO> response =
            controller.importarFacturaAlCatalogo(file);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        String jobId = response.getBody().jobId();
        await().untilAsserted(() -> {
            VinylFutureImportJobDTO job = controller.obtenerJobVinylFuture(jobId).getBody();
            assertThat(job).isNotNull();
            assertThat(job.status()).isIn(
                VinylFutureImportJobStatus.COMPLETED,
                VinylFutureImportJobStatus.COMPLETED_WITH_ERRORS
            );
            assertThat(job.importId()).isEqualTo("import-123");
            assertThat(job.summary()).isNotNull();
            assertThat(job.summary().recordsDetected()).isEqualTo(1);
            assertThat(job.summary().recordsImported()).isEqualTo(1);
            assertThat(job.summary().coversFound()).isEqualTo(1);
            assertThat(job.summary().coversDownloaded()).isEqualTo(1);
            assertThat(job.summary().mp3PreviewsFound()).isEqualTo(1);
            assertThat(job.summary().mp3Downloaded()).isEqualTo(1);
            assertThat(job.summary().qrEntriesCreated()).isEqualTo(2);
            assertThat(job.totalItems()).isEqualTo(1);
            assertThat(job.totalQuantity()).isEqualTo(2);
        });
        verify(audioPreviewService).guardarDesdeTracks(10L, storedPage.tracks());
        verify(zipBundleService, never()).buildZip(any(), any(), anyString());
        controller.shutdownImportPool();
    }

    @Test
    void zipEndpointReturnsStoredBatchAsNonEmptyApplicationZip() throws Exception {
        Path zip = Files.createTempFile("vinylfuture-test-", ".zip");
        Files.write(zip, "zip-bytes".getBytes());
        var batch = new VinylFutureImportBatchService.ImportBatch(
            "import-123",
            "codigo,artista\n",
            new LinkedHashMap<>(),
            "VinylFuture_Invoice_INV-42",
            java.time.Instant.now()
        );
        when(importBatchService.find("import-123")).thenReturn(Optional.of(batch));
        when(zipBundleService.buildZip(batch.csv(), batch.pageDataMap(), batch.zipRootName())).thenReturn(zip);

        ImportController controller = new ImportController(
            pdfParser,
            searchService,
            scraperService,
            assetService,
            csvExportService,
            zipBundleService,
            discoRepository,
            shippingOrderService,
            audioPreviewService,
            qrCopyService,
            pricingService,
            importBatchService,
            transactionManager
        );

        ResponseEntity<StreamingResponseBody> response = controller.exportarZipDesdeImport("import-123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).startsWith("VinylFuture_Invoice_INV-42");
        assertThat(response.getHeaders().getContentLength()).isGreaterThan(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toByteArray()).isNotEmpty();
        controller.shutdownImportPool();
    }

    @Test
    void catalogImportMergesExistingStockInsteadOfSkippingDuplicateCode() throws Exception {
        InvoiceItem item = new InvoiceItem(
            "CAT-123", "Invoice Artist", "Invoice Album", "12",
            new BigDecimal("12.00"), 2, new BigDecimal("24.00")
        );
        ParsedInvoice invoice = new ParsedInvoice(
            List.of(item), List.of(), new BigDecimal("24.00"), 2,
            null, null, null, "INV-77", null, null, null, null, null, null, null, null, null, null, null, null
        );
        VinylPageData page = new VinylPageData(
            "https://www.vinylfuture.com/release_Vinyl__123",
            "Invoice Artist", "Invoice Album", "CAT-123", "Test Label", "House", 2025,
            "Germany", "12", "New", "Full metadata", new BigDecimal("13.00"),
            "/api/importar/vinylfuture/media/CAT-123/cover.jpg", null,
            List.of(new TrackInfo("A1", "First Track", "/api/importar/vinylfuture/media/CAT-123/a1.mp3", null))
        );
        Disco existing = Disco.builder()
            .idDisco(10L)
            .codigoInterno("CAT-123")
            .codigoQr("qr-existing")
            .artista("Invoice Artist")
            .album("Invoice Album")
            .estado(EstadoDisco.DISPONIBLE)
            .cantidadCopias(1)
            .build();

        when(pdfParser.parseInvoice(any(byte[].class))).thenReturn(invoice);
        when(discoRepository.existsByNumeroFacturaCompra("INV-77")).thenReturn(false);
        when(searchService.buscar(item)).thenReturn(Optional.of(page.sourceUrl()));
        when(scraperService.scrape(page.sourceUrl())).thenReturn(Optional.of(page));
        when(assetService.storeAssetsWithResult(item, page))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(page, 0, 0, 0));
        when(discoRepository.findByCodigoInterno("CAT-123")).thenReturn(Optional.of(existing));
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(qrCopyService.countAvailableCopies(10L)).thenReturn(1L);
        when(qrCopyService.synchronizeAvailableCopies(any(Disco.class), anyInt()))
            .thenReturn(List.of(
                DiscoQrCopy.builder().idDisco(10L).copyNumber(1).codigoQr("qr-existing").build(),
                DiscoQrCopy.builder().idDisco(10L).copyNumber(2).codigoQr("qr-2").build(),
                DiscoQrCopy.builder().idDisco(10L).copyNumber(3).codigoQr("qr-3").build()
            ));
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        when(importBatchService.store(any(), any(), anyString())).thenReturn("import-merge");
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ImportController controller = new ImportController(
            pdfParser,
            searchService,
            scraperService,
            assetService,
            csvExportService,
            zipBundleService,
            discoRepository,
            shippingOrderService,
            audioPreviewService,
            qrCopyService,
            pricingService,
            importBatchService,
            transactionManager
        );

        ResponseEntity<VinylFutureImportJobStartDTO> response = controller.importarFacturaAlCatalogo(
            new MockMultipartFile("file", "invoice.pdf", "application/pdf", "pdf".getBytes())
        );

        await().untilAsserted(() -> {
            VinylFutureImportJobDTO job = controller.obtenerJobVinylFuture(response.getBody().jobId()).getBody();
            assertThat(job).isNotNull();
            assertThat(job.status()).isIn(
                VinylFutureImportJobStatus.COMPLETED,
                VinylFutureImportJobStatus.COMPLETED_WITH_ERRORS
            );
        });
        assertThat(existing.getCantidadCopias()).isEqualTo(3);
        verify(qrCopyService).synchronizeAvailableCopies(existing, 3);
        controller.shutdownImportPool();
    }
}
