package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.ImportStatus;
import com.sonograma.repository.PedidoItemRepository;
import com.sonograma.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VinylFutureManualImportServiceTest {

    private static final String URL = "https://www.vinylfuture.com/Artist_Title_TEST-1_Vinyl__123";

    @Mock private VinylFutureScraperService scraperService;
    @Mock private VinylFutureAssetService assetService;
    @Mock private VinylFutureCatalogStockService catalogStockService;
    @Mock private CatalogPricingService pricingService;
    @Mock private AudioPreviewService audioPreviewService;
    @Mock private PreVentaCodeMatcher preVentaCodeMatcher;
    @Mock private CsvExportService csvExportService;
    @Mock private ZipBundleService zipBundleService;
    @Mock private PedidoItemRepository pedidoItemRepository;
    @Mock private PedidoRepository pedidoRepository;

    private VinylFutureManualImportService service;
    private VinylPageData page;

    @BeforeEach
    void setUp() {
        service = new VinylFutureManualImportService(
            scraperService,
            assetService,
            catalogStockService,
            pricingService,
            audioPreviewService,
            preVentaCodeMatcher,
            csvExportService,
            zipBundleService,
            pedidoItemRepository,
            pedidoRepository
        );
        page = new VinylPageData(
            URL, "Artista", "Título", "TEST-1", "Sello", "House", 2026,
            "Alemania", "12\"", "Nuevo", "Descripción", java.math.BigDecimal.TEN,
            "/api/importar/vinylfuture/media/TEST-1/cover.jpg", null,
            List.of(new TrackInfo("A1", "Track", "/api/importar/vinylfuture/media/TEST-1/a1.mp3", null))
        );
    }

    @Test
    void validVinylFutureUrlReturnsPreviewAndExistingProductNotice() {
        Disco existing = product(10L, 2);
        stubSearch(page, existing);

        var preview = service.search(URL, null);

        assertThat(preview.catalogueCode()).isEqualTo("TEST-1");
        assertThat(preview.artist()).isEqualTo("Artista");
        assertThat(preview.existingProduct()).isTrue();
        assertThat(preview.existingProductId()).isEqualTo(10L);
        assertThat(preview.coverAvailable()).isTrue();
    }

    @Test
    void invalidOrForeignUrlIsRejectedWithoutExternalCall() {
        assertThatThrownBy(() -> service.search("https://example.com/product/1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El enlace no pertenece a Vinyl Future.");
        verify(scraperService, never()).scrape(anyString());
    }

    @Test
    void newManualProductSupportsQuantityGreaterThanOneAndRetryIsIdempotent() {
        stubSearch(page, null);
        var preview = service.search(URL, null);
        Disco created = product(20L, 3);
        when(catalogStockService.addStock(eq("TEST-1"), eq(3), any(), any()))
            .thenReturn(new VinylFutureCatalogStockService.Resolution(
                created, VinylFutureCatalogStockService.ProductStatus.NEW, 3, 3
            ));

        var first = service.confirm(preview.previewId(), 3);
        var retry = service.confirm(preview.previewId(), 3);

        assertThat(first.catalogueStatus()).isEqualTo("NUEVO");
        assertThat(first.addedCopies()).isEqualTo(3);
        assertThat(retry.alreadyProcessed()).isTrue();
        assertThat(retry.addedCopies()).isZero();
        verify(catalogStockService).addStock(eq("TEST-1"), eq(3), any(), any());
    }

    @Test
    void existingManualProductAddsSelectedStockInsteadOfCreatingDuplicate() {
        Disco existing = product(30L, 5);
        stubSearch(page, existing);
        var preview = service.search(URL, null);
        when(catalogStockService.addStock(eq("TEST-1"), eq(2), any(), any()))
            .thenReturn(new VinylFutureCatalogStockService.Resolution(
                existing, VinylFutureCatalogStockService.ProductStatus.EXISTING, 2, 5
            ));

        var result = service.confirm(preview.previewId(), 2);

        assertThat(result.catalogueStatus()).isEqualTo("EXISTENTE");
        assertThat(result.addedCopies()).isEqualTo(2);
        assertThat(result.resultingStock()).isEqualTo(5);
    }

    @Test
    void missingCoverIsReportedWithoutFabricatingDownload() {
        VinylPageData withoutCover = new VinylPageData(
            page.sourceUrl(), page.artist(), page.title(), page.code(), page.label(), page.genre(),
            page.year(), page.country(), page.format(), page.condition(), page.description(),
            page.purchasePrice(), null, null, page.tracks()
        );
        stubSearch(withoutCover, null);

        var preview = service.search(URL, null);

        assertThat(preview.coverAvailable()).isFalse();
        assertThatThrownBy(() -> service.cover(preview.previewId()))
            .hasMessage("Portada no disponible");
    }

    @Test
    void availableCoverAndSingleProductZipReuseStoredAssets() throws Exception {
        Disco existing = product(35L, 2);
        LocalDateTime existingUpdate = LocalDateTime.of(2026, 8, 31, 14, 53);
        existing.setFechaActualizacion(existingUpdate);
        stubSearch(page, existing);
        var preview = service.search(URL, null);
        when(assetService.relativePath(page.frontImageUrl())).thenReturn("TEST-1/cover.jpg");
        when(assetService.load("TEST-1/cover.jpg")).thenReturn(new ByteArrayResource("cover".getBytes()));
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        Path zip = Files.createTempFile("manual-vinylfuture-", ".zip");
        when(zipBundleService.buildZip(eq("csv"), any(), eq("VinylFuture_Producto_TEST-1")))
            .thenReturn(zip);

        assertThat(service.cover(preview.previewId()).contentLength()).isGreaterThan(0);
        assertThat(service.buildSingleZip(preview.previewId())).isEqualTo(zip);
        assertThat(existing.getFechaActualizacion()).isEqualTo(existingUpdate);
        verify(catalogStockService, never()).addStock(anyString(), anyInt(), any(), any());
    }

    @Test
    void zipFailureAfterConfirmationDoesNotRepeatCatalogueOperation() throws Exception {
        stubSearch(page, null);
        var preview = service.search(URL, null);
        Disco created = product(40L, 1);
        when(catalogStockService.addStock(eq("TEST-1"), eq(1), any(), any()))
            .thenReturn(new VinylFutureCatalogStockService.Resolution(
                created, VinylFutureCatalogStockService.ProductStatus.NEW, 1, 1
            ));
        when(csvExportService.buildCsv(any())).thenReturn("csv");
        when(zipBundleService.buildZip(eq("csv"), any(), anyString()))
            .thenThrow(new java.io.IOException("fallo simulado"));

        var saved = service.confirm(preview.previewId(), 1);

        assertThat(saved.productId()).isEqualTo(40L);
        assertThatThrownBy(() -> service.buildSingleZip(preview.previewId()))
            .isInstanceOf(java.io.IOException.class);
        verify(catalogStockService).addStock(eq("TEST-1"), eq(1), any(), any());
    }

    @Test
    void pendingItemResolutionAddsQuantityOnceAndMarksOriginalRowResolved() {
        Pedido pedido = Pedido.builder()
            .idPedido(50L)
            .numeroFactura("INV-PENDING")
            .origenImportacion("vinylfuture")
            .importStatus(ImportStatus.PARTIALLY_COMPLETED)
            .items(new ArrayList<>())
            .build();
        PedidoItem pending = PedidoItem.builder()
            .idPedidoItem(51L)
            .pedido(pedido)
            .estadoLectura("REVIEW_REQUIRED")
            .textoFuente("línea ambigua")
            .cantidadEstimada(2)
            .build();
        pedido.getItems().add(pending);
        when(pedidoItemRepository.findByIdWithPedido(51L)).thenReturn(Optional.of(pending));
        stubSearchDataOnly(page, null);
        var preview = service.search(URL, 51L);
        when(pedidoItemRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(pending));
        Disco created = product(60L, 2);
        when(catalogStockService.addStock(eq("TEST-1"), eq(2), any(), any()))
            .thenReturn(new VinylFutureCatalogStockService.Resolution(
                created, VinylFutureCatalogStockService.ProductStatus.NEW, 2, 2
            ));

        var result = service.confirm(preview.previewId(), 2);
        var retry = service.confirm(preview.previewId(), 2);

        assertThat(result.pendingItemResolved()).isTrue();
        assertThat(pending.getEstadoLectura()).isEqualTo("RESUELTO");
        assertThat(pending.getCantidad()).isEqualTo(2);
        assertThat(pending.getDisco()).isSameAs(created);
        assertThat(pedido.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(retry.alreadyProcessed()).isTrue();
        verify(catalogStockService).addStock(eq("TEST-1"), eq(2), any(), any());
    }

    @Test
    void listingPendingRowsPreservesInvoiceReviewContext() {
        Pedido pedido = Pedido.builder()
            .idPedido(70L).numeroFactura("INV-70").origenImportacion("vinylfuture").build();
        PedidoItem pending = PedidoItem.builder()
            .idPedidoItem(71L).pedido(pedido).paginaFuente(3).textoFuente("texto original")
            .motivoRevision("cantidad ambigua").cantidadEstimada(4).estadoLectura("REVIEW_REQUIRED").build();
        when(pedidoItemRepository.findPendingVinylFutureReviewItems()).thenReturn(List.of(pending));

        var items = service.listPendingItems();

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.invoiceNumber()).isEqualTo("INV-70");
            assertThat(item.pageNumber()).isEqualTo(3);
            assertThat(item.estimatedQuantity()).isEqualTo(4);
        });
    }

    private void stubSearch(VinylPageData storedPage, Disco existing) {
        stubSearchDataOnly(storedPage, existing);
        when(assetService.localPath(storedPage.frontImageUrl()))
            .thenReturn(storedPage.frontImageUrl() == null ? null : Path.of("/tmp/cover.jpg"));
    }

    private void stubSearchDataOnly(VinylPageData storedPage, Disco existing) {
        when(scraperService.scrape(URL)).thenReturn(Optional.of(storedPage));
        when(assetService.storeAssetsWithResult(any(InvoiceItem.class), eq(storedPage)))
            .thenReturn(new VinylFutureAssetService.AssetStoreResult(storedPage, 0, 0, 0));
        when(catalogStockService.preview(storedPage.code()))
            .thenReturn(new VinylFutureCatalogStockService.Resolution(
                existing,
                existing == null
                    ? VinylFutureCatalogStockService.ProductStatus.NEW
                    : VinylFutureCatalogStockService.ProductStatus.EXISTING,
                0,
                existing == null || existing.getCantidadCopias() == null ? 0 : existing.getCantidadCopias()
            ));
    }

    private Disco product(Long id, int stock) {
        return Disco.builder()
            .idDisco(id)
            .codigoInterno("TEST-1")
            .vinylFutureSupplierCodeNormalized("TEST-1")
            .artista("Artista")
            .album("Título")
            .estado(EstadoDisco.DISPONIBLE)
            .cantidadCopias(stock)
            .build();
    }
}
