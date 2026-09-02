package com.sonograma.service;

import com.sonograma.dto.ParsedInvoice;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.ImportStatus;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PedidoItemRepository;
import com.sonograma.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceVinylFutureIdempotencyTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoItemRepository pedidoItemRepository;
    @Mock private PdfInvoiceParser pdfParser;
    @Mock private PedidoEnrichmentService enrichmentService;
    @Mock private DiscoRepository discoRepository;
    @Mock private AudioPreviewService audioPreviewService;
    @Mock private DiscoQrCopyService qrCopyService;
    @Mock private CatalogPricingService catalogPricingService;
    @Mock private VinylFutureCatalogStockService catalogStockService;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(
            pedidoRepository,
            pedidoItemRepository,
            pdfParser,
            enrichmentService,
            discoRepository,
            audioPreviewService,
            qrCopyService,
            catalogPricingService,
            new VinylFutureIdentityNormalizer(),
            catalogStockService
        );
    }

    @Test
    void completedInvoiceIsBlockedBeforeAnyStockOperation() {
        LocalDateTime existingUpdate = LocalDateTime.of(2026, 8, 31, 14, 53);
        Disco existingProduct = Disco.builder()
            .idDisco(699L)
            .artista("Raxon")
            .album("Speicher 138")
            .fechaActualizacion(existingUpdate)
            .build();
        Pedido completed = Pedido.builder()
            .numeroFactura("0036-188471")
            .origenImportacion("vinylfuture")
            .vinylFutureOperationKey("VINYLFUTURE:0036-188471")
            .importStatus(ImportStatus.COMPLETED)
            .build();
        when(pedidoRepository.findByVinylFutureOperationKey("VINYLFUTURE:0036-188471"))
            .thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.verificarFacturaVinylFutureImportable(" 0036-188471 "))
            .isInstanceOf(ConflictoNegocioException.class)
            .hasMessage("Esta factura ya fue importada.");
        verify(discoRepository, never()).existsByNumeroFacturaCompra("0036-188471");
        verify(catalogStockService, never()).addStock(anyString(), anyInt(), any(), any());
        assertThat(existingProduct.getFechaActualizacion()).isEqualTo(existingUpdate);
    }

    @Test
    void failedInvoiceReusesPedidoAndMovesBackToImportingWithoutDuplicatingIt() {
        Pedido failed = Pedido.builder()
            .idPedido(80L)
            .numeroFactura("INV-80")
            .origenImportacion("vinylfuture")
            .importStatus(ImportStatus.FAILED)
            .items(new ArrayList<>())
            .build();
        when(pedidoRepository.findVinylFutureOperationForUpdate("VINYLFUTURE:INV-80"))
            .thenReturn(Optional.empty());
        when(pedidoRepository.findByOrigenImportacionAndNumeroFactura("vinylfuture", "INV-80"))
            .thenReturn(List.of(failed));
        when(pedidoRepository.save(failed)).thenReturn(failed);

        Pedido result = service.persistirVinylFuture(
            "pdf".getBytes(), "invoice.pdf", invoice("INV-80"), List.of(), false
        );

        assertThat(result).isSameAs(failed);
        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.IMPORTING_TO_CATALOG);
        assertThat(result.getVinylFutureOperationKey()).isEqualTo("VINYLFUTURE:INV-80");
        verify(pedidoRepository).save(failed);
    }

    @Test
    void sameInvoiceAlreadyImportingIsRejectedAsDoubleSubmit() {
        Pedido importing = Pedido.builder()
            .numeroFactura("INV-81")
            .vinylFutureOperationKey("VINYLFUTURE:INV-81")
            .importStatus(ImportStatus.IMPORTING_TO_CATALOG)
            .build();
        when(pedidoRepository.findVinylFutureOperationForUpdate("VINYLFUTURE:INV-81"))
            .thenReturn(Optional.of(importing));

        assertThatThrownBy(() -> service.persistirVinylFuture(
            "pdf".getBytes(), "invoice.pdf", invoice("INV-81"), List.of(), false
        )).isInstanceOf(ConflictoNegocioException.class)
            .hasMessage("Esta factura ya se está importando.");
    }

    @Test
    void historicalDuplicateInvoiceRecordsAreReportedWithoutAutomaticMerge() {
        when(pedidoRepository.findByVinylFutureOperationKey("VINYLFUTURE:INV-DUP"))
            .thenReturn(Optional.empty());
        when(pedidoRepository.findByOrigenImportacionAndNumeroFactura("vinylfuture", "INV-DUP"))
            .thenReturn(List.of(
                Pedido.builder().idPedido(1L).build(),
                Pedido.builder().idPedido(2L).build()
            ));

        assertThatThrownBy(() -> service.verificarFacturaVinylFutureImportable("INV-DUP"))
            .isInstanceOf(ConflictoNegocioException.class)
            .hasMessage("La factura tiene registros históricos duplicados. Revisalos antes de volver a importarla.");
    }

    @Test
    void phaseOneParsedPedidoWithExistingInvoiceStockIsBlockedAsAlreadyImported() {
        Pedido legacy = Pedido.builder()
            .idPedido(3L)
            .numeroFactura("INV-LEGACY")
            .origenImportacion("vinylfuture")
            .importStatus(ImportStatus.PARSED)
            .items(new ArrayList<>())
            .build();
        when(pedidoRepository.findByVinylFutureOperationKey("VINYLFUTURE:INV-LEGACY"))
            .thenReturn(Optional.empty());
        when(pedidoRepository.findByOrigenImportacionAndNumeroFactura("vinylfuture", "INV-LEGACY"))
            .thenReturn(List.of(legacy));
        when(discoRepository.existsByNumeroFacturaCompra("INV-LEGACY")).thenReturn(true);

        assertThatThrownBy(() -> service.verificarFacturaVinylFutureImportable("INV-LEGACY"))
            .isInstanceOf(ConflictoNegocioException.class)
            .hasMessage("Esta factura ya fue importada.");
    }

    @Test
    void successfulProductLinksAllRepeatedSourceRowsForRetrySafety() {
        Pedido pedido = Pedido.builder().idPedido(90L).items(new ArrayList<>()).build();
        PedidoItem first = PedidoItem.builder().pedido(pedido).codigo(" oyster80 ").build();
        PedidoItem second = PedidoItem.builder().pedido(pedido).codigo("OYSTER80").build();
        pedido.getItems().addAll(List.of(first, second));
        Disco disco = Disco.builder().idDisco(91L).artista("A").album("B").build();
        when(pedidoItemRepository.findByPedidoIdPedido(90L)).thenReturn(List.of(first, second));

        service.marcarProductoVinylFutureImportado(90L, "OYSTER80", disco);

        assertThat(first.getDisco()).isSameAs(disco);
        assertThat(second.getDisco()).isSameAs(disco);
        assertThat(first.getEstadoLectura()).isEqualTo("IMPORTADO");
        verify(pedidoItemRepository).saveAll(List.of(first, second));
    }

    private ParsedInvoice invoice(String number) {
        return new ParsedInvoice(
            List.of(), List.of(), BigDecimal.ZERO, 0,
            null, null, null, number, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null
        );
    }
}
