package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VinylFutureCatalogStockServiceTest {

    @Mock private DiscoRepository discoRepository;
    @Mock private DiscoQrCopyService qrCopyService;

    private VinylFutureCatalogStockService service;

    @BeforeEach
    void setUp() {
        service = new VinylFutureCatalogStockService(
            discoRepository, qrCopyService, new VinylFutureIdentityNormalizer()
        );
    }

    @Test
    void newIdentityCreatesOneProductAndRequestedPhysicalCopies() {
        when(discoRepository.findVinylFutureByIdentityForUpdate("GM-05")).thenReturn(Optional.empty());
        when(discoRepository.findAllActiveWithCatalogCode()).thenReturn(List.of());
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> {
            Disco disco = invocation.getArgument(0);
            disco.setIdDisco(10L);
            return disco;
        });

        VinylFutureCatalogStockService.Resolution result = service.addStock(
            "  gm-05  ", 3, this::newProduct, ignored -> { }
        );

        assertThat(result.status()).isEqualTo(VinylFutureCatalogStockService.ProductStatus.NEW);
        assertThat(result.addedCopies()).isEqualTo(3);
        assertThat(result.resultingStock()).isEqualTo(3);
        assertThat(result.disco().getVinylFutureSupplierCodeNormalized()).isEqualTo("GM-05");
        assertThat(result.disco().getCantidadCopias()).isEqualTo(3);
        verify(qrCopyService).synchronizeAvailableCopies(result.disco(), 3);
    }

    @Test
    void existingIdentityAddsStockWithoutOverwritingCurrentStock() {
        Disco existing = newProduct();
        existing.setIdDisco(20L);
        existing.setCantidadCopias(2);
        existing.setVinylFutureSupplierCodeNormalized("OYSTER80");
        when(discoRepository.findVinylFutureByIdentityForUpdate("OYSTER80"))
            .thenReturn(Optional.of(existing));
        when(qrCopyService.hasCopyInventory(20L)).thenReturn(false);
        when(discoRepository.save(existing)).thenReturn(existing);

        VinylFutureCatalogStockService.Resolution result = service.addStock(
            "oyster80", 4, this::newProduct, disco -> disco.setGenero("House")
        );

        assertThat(result.status()).isEqualTo(VinylFutureCatalogStockService.ProductStatus.EXISTING);
        assertThat(result.resultingStock()).isEqualTo(6);
        assertThat(existing.getCantidadCopias()).isEqualTo(6);
        assertThat(existing.getGenero()).isEqualTo("House");
        verify(qrCopyService).synchronizeAvailableCopies(existing, 6);
    }

    @Test
    void usesAvailableQrInventoryAsStockBaseAndAddsOnlyIncomingCopies() {
        Disco existing = newProduct();
        existing.setIdDisco(21L);
        existing.setCantidadCopias(99);
        existing.setVinylFutureSupplierCodeNormalized("MAO-V001");
        when(discoRepository.findVinylFutureByIdentityForUpdate("MAO-V001"))
            .thenReturn(Optional.of(existing));
        when(qrCopyService.hasCopyInventory(21L)).thenReturn(true);
        when(qrCopyService.countAvailableCopies(21L)).thenReturn(2L);
        when(discoRepository.save(existing)).thenReturn(existing);

        var result = service.addStock("MAO-V001", 2, this::newProduct, ignored -> { });

        assertThat(result.resultingStock()).isEqualTo(4);
        verify(qrCopyService).synchronizeAvailableCopies(existing, 4);
    }

    @Test
    void laterLegitimateOperationAddsStockAgainToSameProduct() {
        Disco existing = newProduct();
        existing.setIdDisco(30L);
        existing.setCantidadCopias(1);
        existing.setVinylFutureSupplierCodeNormalized("LATER-1");
        when(discoRepository.findVinylFutureByIdentityForUpdate("LATER-1"))
            .thenReturn(Optional.of(existing));
        when(qrCopyService.hasCopyInventory(30L)).thenReturn(false);
        when(discoRepository.save(existing)).thenReturn(existing);

        service.addStock("later-1", 1, this::newProduct, ignored -> { });
        service.addStock("LATER-1", 2, this::newProduct, ignored -> { });

        assertThat(existing.getCantidadCopias()).isEqualTo(4);
        verify(qrCopyService).synchronizeAvailableCopies(existing, 2);
        verify(qrCopyService).synchronizeAvailableCopies(existing, 4);
    }

    @Test
    void distinctPunctuatedSupplierCodesRemainDistinct() {
        when(discoRepository.findVinylFutureByIdentityForUpdate("GM-05")).thenReturn(Optional.empty());
        when(discoRepository.findVinylFutureByIdentityForUpdate("GM05")).thenReturn(Optional.empty());
        when(discoRepository.findAllActiveWithCatalogCode()).thenReturn(List.of());
        when(discoRepository.save(any(Disco.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Disco first = service.addStock("GM-05", 1, this::newProduct, ignored -> { }).disco();
        Disco second = service.addStock("GM05", 1, this::newProduct, ignored -> { }).disco();

        assertThat(first.getVinylFutureSupplierCodeNormalized()).isEqualTo("GM-05");
        assertThat(second.getVinylFutureSupplierCodeNormalized()).isEqualTo("GM05");
        assertThat(first.getVinylFutureSupplierCodeNormalized())
            .isNotEqualTo(second.getVinylFutureSupplierCodeNormalized());
    }

    @Test
    void previewNeverChangesStockOrQrCopies() {
        Disco existing = newProduct();
        existing.setVinylFutureSupplierCodeNormalized("PREVIEW-1");
        when(discoRepository.findByVinylFutureSupplierCodeNormalized("PREVIEW-1"))
            .thenReturn(Optional.of(existing));

        var result = service.preview("preview-1");

        assertThat(result.status()).isEqualTo(VinylFutureCatalogStockService.ProductStatus.EXISTING);
        verify(qrCopyService, never()).synchronizeAvailableCopies(any(), any(Integer.class));
    }

    @Test
    void normalizationHandlesWhitespaceCaseAndUnicodeWithoutRemovingPunctuation() {
        VinylFutureIdentityNormalizer normalizer = new VinylFutureIdentityNormalizer();

        assertThat(normalizer.normalize("  mao-v001  ")).isEqualTo("MAO-V001");
        assertThat(normalizer.normalize("ＡＢＣ  12" )).isEqualTo("ABC 12");
        assertThat(normalizer.normalize("GM-05")).isNotEqualTo(normalizer.normalize("GM05"));
    }

    @Test
    void legacyFutureProductWithFormattingDifferencesIsReused() {
        Disco legacy = newProduct();
        legacy.setIdDisco(88L);
        legacy.setCodigoInterno(" mao-v001   ");
        legacy.setCantidadCopias(1);
        when(discoRepository.findVinylFutureByIdentityForUpdate("MAO-V001")).thenReturn(Optional.empty());
        when(discoRepository.findAllActiveWithCatalogCode()).thenReturn(List.of(legacy));
        when(qrCopyService.hasCopyInventory(88L)).thenReturn(false);
        when(discoRepository.save(legacy)).thenReturn(legacy);

        var result = service.addStock("MAO-V001", 1, this::newProduct, ignored -> { });

        assertThat(result.status()).isEqualTo(VinylFutureCatalogStockService.ProductStatus.EXISTING);
        assertThat(result.disco()).isSameAs(legacy);
        assertThat(legacy.getVinylFutureSupplierCodeNormalized()).isEqualTo("MAO-V001");
        assertThat(legacy.getCantidadCopias()).isEqualTo(2);
    }

    @Test
    void ambiguousLegacyIdentityIsReportedWithoutMergingRecords() {
        Disco first = newProduct();
        first.setCodigoInterno("DUP-1");
        Disco second = newProduct();
        second.setCodigoInterno(" dup-1 ");
        when(discoRepository.findVinylFutureByIdentityForUpdate("DUP-1")).thenReturn(Optional.empty());
        when(discoRepository.findAllActiveWithCatalogCode()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.addStock("DUP-1", 1, this::newProduct, ignored -> { }))
            .hasMessageContaining("más de un producto Vinyl Future");
        verify(discoRepository, never()).save(any(Disco.class));
    }

    private Disco newProduct() {
        return Disco.builder()
            .artista("Artista")
            .album("Álbum")
            .estado(EstadoDisco.DISPONIBLE)
            .procedencia(ImportMetadataNormalizer.SOURCE_FUTURE)
            .cantidadCopias(0)
            .build();
    }
}
