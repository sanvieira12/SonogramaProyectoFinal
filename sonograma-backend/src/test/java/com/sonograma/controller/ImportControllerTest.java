package com.sonograma.controller;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylFutureImportSummaryDTO;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
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
import com.sonograma.service.ZipBundleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks private ImportController controller;

    @Test
    void catalogImportPersistsStructuredDataWithoutBuildingZip() throws Exception {
        InvoiceItem item = new InvoiceItem(
            "INV-001", "Invoice Artist", "Invoice Album", "2x12",
            new BigDecimal("12.00"), 2, new BigDecimal("24.00")
        );
        ParsedInvoice invoice = new ParsedInvoice(
            List.of(item), List.of(), new BigDecimal("24.00"), 2,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        VinylPageData page = new VinylPageData(
            "https://www.vinylfuture.com/release_Vinyl__123",
            "Scraped Artist", "Scraped Album", "CAT-123", "Test Label", "House", 2025,
            "Germany", "2x12", "New", "Full metadata", new BigDecimal("13.00"),
            "https://cdn.example/cover.jpg", null,
            List.of(new TrackInfo("A1", "First Track", "https://cdn.example/a1.mp3", null))
        );

        when(pdfParser.parseInvoice(any(byte[].class))).thenReturn(invoice);
        when(searchService.buscar(item)).thenReturn(Optional.of(page.sourceUrl()));
        when(scraperService.scrape(page.sourceUrl())).thenReturn(Optional.of(page));
        when(assetService.storeAssets(item, page)).thenReturn(page);
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
        when(pricingService.calcular(new BigDecimal("12.00"), "2x12"))
            .thenReturn(new CatalogPricingService.PricingResult(
                new BigDecimal("8"), new BigDecimal("20"), new BigDecimal("980"),
                new BigDecimal("1.4"), new BigDecimal("1372")
            ));

        MockMultipartFile file = new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf", "pdf".getBytes()
        );
        ResponseEntity<VinylFutureImportSummaryDTO> response =
            controller.importarFacturaAlCatalogo(file);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().recordsDetected()).isEqualTo(1);
        assertThat(response.getBody().recordsImported()).isEqualTo(1);
        assertThat(response.getBody().coversFound()).isEqualTo(1);
        assertThat(response.getBody().mp3PreviewsFound()).isEqualTo(1);
        assertThat(response.getBody().qrEntriesCreated()).isEqualTo(2);
        verify(audioPreviewService).guardarDesdeTracks(10L, page.tracks());
        verify(zipBundleService, never()).buildZip(any(), any());
    }
}
