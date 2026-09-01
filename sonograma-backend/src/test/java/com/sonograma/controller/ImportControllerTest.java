package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.InvoiceParseResult;
import com.sonograma.dto.InvoiceSourceRowDTO;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylFutureImportJobDTO;
import com.sonograma.dto.VinylFutureImportJobStartDTO;
import com.sonograma.dto.VinylFutureInvoiceValidationDTO;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.Pedido;
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
import com.sonograma.service.VinylFutureManualImportService;
import com.sonograma.service.VinylFutureCatalogStockService;
import com.sonograma.service.VinylFutureIdentityNormalizer;
import com.sonograma.service.ZipBundleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
    @Mock private com.sonograma.service.PedidoService pedidoService;
    @Mock private VinylFutureManualImportService manualImportService;
    @Mock private VinylFutureCatalogStockService catalogStockService;
    @Mock private PlatformTransactionManager transactionManager;
    private final VinylFutureIdentityNormalizer identityNormalizer = new VinylFutureIdentityNormalizer();

    @BeforeEach
    void setUpPedido() {
        Pedido pedido = Pedido.builder().idPedido(501L).items(new java.util.ArrayList<>()).build();
        lenient().when(pedidoService.persistirVinylFuture(any(), anyString(), any(), any(), anyBoolean()))
            .thenReturn(pedido);
        lenient().when(pedidoService.identidadesVinylFutureImportadas(501L)).thenReturn(java.util.Set.of());
    }

    @Test
    void catalogImportPersistsStructuredDataWithoutBuildingZip() throws Exception {
        final Disco[] savedDisco = new Disco[1];
        InvoiceItem item = new InvoiceItem(
            "CAT-123", "Invoice Artist", "Invoice Album", "2x12",
            new BigDecimal("12.00"), 2, new BigDecimal("24.00")
        );
        ParsedInvoice invoice = new ParsedInvoice(
            List.of(item), List.of(), new BigDecimal("24.00"), 2,
            null, null, null, "0031-188471", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
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

        when(pdfParser.parseInvoiceWithDiagnostics(any(byte[].class))).thenReturn(parseResult(invoice));
        when(searchService.buscar(item)).thenReturn(Optional.of(page.sourceUrl()));
        when(scraperService.scrape(page.sourceUrl())).thenReturn(Optional.of(page));
        when(assetService.storeAssetsWithResult(item, page))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(storedPage, 1, 1, 0));
        when(catalogStockService.addStock(eq("CAT-123"), eq(2), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<Disco> factory = invocation.getArgument(2);
            Disco disco = factory.get();
            savedDisco[0] = disco;
            disco.setIdDisco(10L);
            return new VinylFutureCatalogStockService.Resolution(
                disco, VinylFutureCatalogStockService.ProductStatus.NEW, 2, 2
            );
        });
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        Path prebuiltZip = Files.createTempFile("vinylfuture-ready-", ".zip");
        Files.write(prebuiltZip, "ready".getBytes());
        when(zipBundleService.buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_0031-188471"))).thenReturn(prebuiltZip);
        when(importBatchService.store(any(), any(), anyString(), eq(prebuiltZip))).thenReturn("import-123");
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
            pedidoService,
            manualImportService,
            catalogStockService,
            identityNormalizer,
            org.mockito.Mockito.mock(com.sonograma.service.PreVentaCodeMatcher.class),
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
        assertThat(savedDisco[0]).isNotNull();
        assertThat(savedDisco[0].getProcedencia()).isEqualTo("Future");
        verify(audioPreviewService).guardarDesdeTracks(10L, storedPage.tracks());
        verify(zipBundleService).buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_0031-188471"));
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
            zip,
            java.time.Instant.now()
        );
        when(importBatchService.find("import-123")).thenReturn(Optional.of(batch));

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
            pedidoService,
            manualImportService,
            catalogStockService,
            identityNormalizer,
            org.mockito.Mockito.mock(com.sonograma.service.PreVentaCodeMatcher.class),
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
        verify(zipBundleService, never()).buildZip(any(), any(), anyString());
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
            null, null, null, "INV-77", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
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

        when(pdfParser.parseInvoiceWithDiagnostics(any(byte[].class))).thenReturn(parseResult(invoice));
        when(searchService.buscar(item)).thenReturn(Optional.of(page.sourceUrl()));
        when(scraperService.scrape(page.sourceUrl())).thenReturn(Optional.of(page));
        when(assetService.storeAssetsWithResult(item, page))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(page, 0, 0, 0));
        when(catalogStockService.addStock(eq("CAT-123"), eq(2), any(), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<Disco> enricher = invocation.getArgument(3);
            enricher.accept(existing);
            existing.setCantidadCopias(3);
            return new VinylFutureCatalogStockService.Resolution(
                existing, VinylFutureCatalogStockService.ProductStatus.EXISTING, 2, 3
            );
        });
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        Path prebuiltZip = Files.createTempFile("vinylfuture-merge-", ".zip");
        Files.write(prebuiltZip, "ready".getBytes());
        when(zipBundleService.buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_INV-77"))).thenReturn(prebuiltZip);
        when(importBatchService.store(any(), any(), anyString(), eq(prebuiltZip))).thenReturn("import-merge");
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
            pedidoService,
            manualImportService,
            catalogStockService,
            identityNormalizer,
            org.mockito.Mockito.mock(com.sonograma.service.PreVentaCodeMatcher.class),
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
        verify(catalogStockService).addStock(eq("CAT-123"), eq(2), any(), any());
        controller.shutdownImportPool();
    }

    @Test
    void cancellingAfterValidationDiscrepancyDoesNotPersistOrImportAnything() throws Exception {
        InvoiceItem valid = new InvoiceItem(
            "VALID-1", "Artista", "Título", "12",
            new BigDecimal("10.00"), 1, new BigDecimal("10.00")
        );
        ParsedInvoice invoice = invoice(List.of(valid), 2, "INV-PARCIAL");
        InvoiceSourceRowDTO parsedRow = new InvoiceSourceRowDTO(
            1, 1, "VALID-1 - Artista- Título 10,00 1 10,00",
            "PARSED", 1, null, valid
        );
        InvoiceSourceRowDTO pendingRow = new InvoiceSourceRowDTO(
            2, 2, "ROTA-2 - Artista- Título 10,00 X 10,00",
            "REVIEW_REQUIRED", 1, "No se pudo determinar la cantidad.", null
        );
        when(pdfParser.parseInvoiceWithDiagnostics(any(byte[].class))).thenReturn(
            new InvoiceParseResult(invoice, List.of(parsedRow, pendingRow), List.of(),
                List.of("La cantidad declarada (2) no coincide con las copias interpretadas (1)."))
        );

        ImportController controller = controller();
        VinylFutureInvoiceValidationDTO validation = controller.validarFacturaVinylFuture(
            new MockMultipartFile("file", "invoice.pdf", "application/pdf", "pdf".getBytes())
        ).getBody();

        assertThat(validation).isNotNull();
        assertThat(validation.consistent()).isFalse();
        assertThat(validation.pendingPhysicalQuantity()).isEqualTo(1);
        assertThat(controller.cancelarValidacionVinylFuture(validation.validationId()).getStatusCode().value())
            .isEqualTo(204);
        verify(pedidoService, never()).persistirVinylFuture(any(), anyString(), any(), any(), eq(true));
        verify(searchService, never()).buscar(any());
        controller.shutdownImportPool();
    }

    @Test
    void acceptedPartialImportProcessesOnlyValidItemsAndKeepsPendingRowInResult() throws Exception {
        InvoiceItem valid = new InvoiceItem(
            "VALID-1", "Artista", "Título", "12",
            new BigDecimal("10.00"), 1, new BigDecimal("10.00")
        );
        ParsedInvoice invoice = invoice(List.of(valid), 2, "INV-PARCIAL-2");
        InvoiceSourceRowDTO parsedRow = new InvoiceSourceRowDTO(
            1, 1, "VALID-1 - Artista- Título 10,00 1 10,00",
            "PARSED", 1, null, valid
        );
        InvoiceSourceRowDTO pendingRow = new InvoiceSourceRowDTO(
            2, 2, "ROTA-2 - Artista- Título 10,00 X 10,00",
            "REVIEW_REQUIRED", 1, "No se pudo determinar la cantidad.", null
        );
        when(pdfParser.parseInvoiceWithDiagnostics(any(byte[].class))).thenReturn(
            new InvoiceParseResult(invoice, List.of(parsedRow, pendingRow), List.of(),
                List.of("La cantidad declarada (2) no coincide con las copias interpretadas (1)."))
        );
        when(searchService.buscar(valid)).thenReturn(Optional.empty());
        when(catalogStockService.addStock(eq("VALID-1"), eq(1), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<Disco> factory = invocation.getArgument(2);
            Disco disco = factory.get();
            disco.setIdDisco(90L);
            return new VinylFutureCatalogStockService.Resolution(
                disco, VinylFutureCatalogStockService.ProductStatus.NEW, 1, 1
            );
        });
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        Path zip = Files.createTempFile("vinylfuture-partial-", ".zip");
        when(zipBundleService.buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_INV-PARCIAL-2")))
            .thenReturn(zip);
        when(importBatchService.store(any(), any(), anyString(), eq(zip))).thenReturn("partial-1");
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ImportController controller = controller();
        VinylFutureInvoiceValidationDTO validation = controller.validarFacturaVinylFuture(
            new MockMultipartFile("file", "invoice.pdf", "application/pdf", "pdf".getBytes())
        ).getBody();
        ResponseEntity<VinylFutureImportJobStartDTO> started = controller.confirmarFacturaVinylFuture(
            validation.validationId(), true
        );

        await().untilAsserted(() -> {
            VinylFutureImportJobDTO job = controller.obtenerJobVinylFuture(started.getBody().jobId()).getBody();
            assertThat(job.summary()).isNotNull();
            assertThat(job.summary().partialImport()).isTrue();
            assertThat(job.summary().importedCopies()).isEqualTo(1);
            assertThat(job.summary().pendingCopies()).isEqualTo(1);
            assertThat(job.summary().failedLinks()).isEqualTo(1);
            assertThat(job.sourceRows()).contains(pendingRow);
            assertThat(job.sourceRows()).contains(parsedRow);
            assertThat(job.currentStep()).isEqualTo("Importación completada con elementos pendientes");
        });
        verify(searchService).buscar(valid);
        verify(pedidoService).persistirVinylFuture(
            any(), eq("invoice.pdf"), eq(invoice), eq(List.of(parsedRow, pendingRow)), eq(true)
        );
        verify(zipBundleService).buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_INV-PARCIAL-2"));
        controller.shutdownImportPool();
    }

    @Test
    void zipFailureIsReportedSeparatelyAfterCatalogImportSucceeds() throws Exception {
        InvoiceItem item = new InvoiceItem(
            "ZIP-1", "Artista", "Título", "12",
            BigDecimal.TEN, 1, BigDecimal.TEN
        );
        ParsedInvoice invoice = invoice(List.of(item), 1, "INV-ZIP");
        when(pdfParser.parseInvoiceWithDiagnostics(any(byte[].class))).thenReturn(parseResult(invoice));
        when(searchService.buscar(item)).thenReturn(Optional.empty());
        when(catalogStockService.addStock(eq("ZIP-1"), eq(1), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<Disco> factory = invocation.getArgument(2);
            Disco disco = factory.get();
            disco.setIdDisco(91L);
            return new VinylFutureCatalogStockService.Resolution(
                disco, VinylFutureCatalogStockService.ProductStatus.NEW, 1, 1
            );
        });
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        when(zipBundleService.buildZip(eq("csv"), any(), eq("VinylFuture_Invoice_INV-ZIP")))
            .thenThrow(new IllegalStateException("fallo técnico simulado"));
        when(importBatchService.store(any(), any(), anyString(), eq(null))).thenReturn("zip-retry-1");
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ImportController controller = controller();
        ResponseEntity<VinylFutureImportJobStartDTO> started = controller.importarFacturaAlCatalogo(
            new MockMultipartFile("file", "invoice.pdf", "application/pdf", "pdf".getBytes())
        );

        await().untilAsserted(() -> {
            VinylFutureImportJobDTO job = controller.obtenerJobVinylFuture(started.getBody().jobId()).getBody();
            assertThat(job.status()).isEqualTo(VinylFutureImportJobStatus.COMPLETED_WITH_ERRORS);
            assertThat(job.summary()).isNotNull();
            assertThat(job.summary().importedCopies()).isEqualTo(1);
            assertThat(job.summary().zipStatus()).isEqualTo("FALLIDO");
            assertThat(job.warnings()).anyMatch(warning -> warning.startsWith("No se pudo generar el archivo ZIP"));
        });
        controller.shutdownImportPool();
    }

    private ImportController controller() {
        return new ImportController(
            pdfParser, searchService, scraperService, assetService, csvExportService,
            zipBundleService, discoRepository, shippingOrderService, audioPreviewService,
            qrCopyService, pricingService, importBatchService, pedidoService,
            manualImportService, catalogStockService, identityNormalizer,
            org.mockito.Mockito.mock(com.sonograma.service.PreVentaCodeMatcher.class),
            transactionManager
        );
    }

    private ParsedInvoice invoice(List<InvoiceItem> items, int declaredQuantity, String number) {
        return new ParsedInvoice(
            items, List.of(), BigDecimal.TEN, declaredQuantity,
            null, null, null, number, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null
        );
    }

    private InvoiceParseResult parseResult(ParsedInvoice invoice) {
        List<InvoiceSourceRowDTO> rows = new java.util.ArrayList<>();
        for (int index = 0; index < invoice.items().size(); index++) {
            InvoiceItem item = invoice.items().get(index);
            rows.add(new InvoiceSourceRowDTO(
                index + 1,
                1,
                item.codigoCatalogo() + " - " + item.artista() + "- " + item.album(),
                "PARSED",
                item.cantidad(),
                null,
                item
            ));
        }
        return new InvoiceParseResult(invoice, List.copyOf(rows), List.of(), List.of());
    }
}
