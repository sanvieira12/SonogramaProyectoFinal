package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
class DiscogsCatalogStockServiceTest {

    @Autowired private DiscogsCatalogStockService service;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private DiscoQrCopyService qrCopyService;

    @BeforeEach
    void clean() {
        copyRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void repeatedConcreteReleaseReceiptsCreateOneProductAndThreeAvailableQrCopies() {
        DiscogsCatalogStockService.ReceiptResult first = service.receive(command(456L, 1));
        DiscogsCatalogStockService.ReceiptResult second = service.receive(command(456L, 2));

        assertThat(first.productStatus()).isEqualTo(DiscogsCatalogStockService.ProductStatus.NEW_PRODUCT);
        assertThat(second.productStatus()).isEqualTo(DiscogsCatalogStockService.ProductStatus.EXISTING_PRODUCT);
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getIdDisco()).isEqualTo(first.disco().getIdDisco());
            assertThat(disco.getDiscogsReleaseId()).isEqualTo(456L);
            assertThat(disco.getDiscogsUrl()).isEqualTo("https://www.discogs.com/release/456");
            assertThat(disco.getCantidadCopias()).isEqualTo(3);
        });
        assertThat(qrCopyService.countAvailableCopies(first.disco().getIdDisco())).isEqualTo(3);
    }

    @Test
    void deterministicLegacyCanonicalUrlIsReusedAndBackfilled() {
        Disco legacy = Disco.builder()
                .codigoQr(UUID.randomUUID().toString())
                .artista("Legacy")
                .album("Release")
                .condicion(CondicionDisco.USADO)
                .tipoDisco(TipoDisco.VINILO)
                .pricingMode(PricingMode.AUTO)
                .discogsUrl("https://www.discogs.com/release/456")
                .cantidadCopias(1)
                .build();
        legacy = discoRepository.save(legacy);

        DiscogsCatalogStockService.ReceiptResult result = service.receive(command(456L, 1));

        assertThat(result.productStatus()).isEqualTo(DiscogsCatalogStockService.ProductStatus.EXISTING_PRODUCT);
        assertThat(result.disco().getIdDisco()).isEqualTo(legacy.getIdDisco());
        assertThat(result.disco().getDiscogsReleaseId()).isEqualTo(456L);
        assertThat(result.resultingAvailableCopies()).isEqualTo(2);
    }

    @Test
    void ambiguousIdentityDoesNotSilentlyMerge() {
        discoRepository.save(newLegacy(456L));
        discoRepository.save(newLegacy(456L));

        assertThatThrownBy(() -> service.receive(command(456L, 1)))
                .isInstanceOf(ConflictoNegocioException.class)
                .hasMessageContaining("más de un producto");
    }

    @Test
    void addingStockDoesNotAlterSoldQrCopies() {
        Disco disco = service.receive(command(456L, 2)).disco();
        qrCopyService.reserveCopies(disco, 1, null, null);

        service.receive(command(456L, 1));

        assertThat(copyRepository.findByIdDiscoOrderByCopyNumber(disco.getIdDisco()))
                .filteredOn(copy -> copy.getEstado() == EstadoCopiaDisco.VENDIDO).hasSize(1);
        assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(2);
    }

    private Disco newLegacy(Long releaseId) {
        return Disco.builder()
                .codigoQr(UUID.randomUUID().toString())
                .artista("Legacy")
                .album("Release " + UUID.randomUUID())
                .condicion(CondicionDisco.USADO)
                .tipoDisco(TipoDisco.VINILO)
                .pricingMode(PricingMode.AUTO)
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .cantidadCopias(1)
                .build();
    }

    private DiscogsCatalogStockService.ReceiptCommand command(long releaseId, int copies) {
        return new DiscogsCatalogStockService.ReceiptCommand(releaseId, copies,
                new DiscogsCatalogStockService.DiscogsMetadata(
                        "Artist", "Album", "Electronic", "Label", 2000,
                        CondicionDisco.USADO, "VG+", TipoDisco.VINILO, "VINILO",
                        null, null, PricingMode.AUTO, "Uruguay", "Techno", "A1. Track",
                        null, null, "A-2000-" + releaseId, "DISCOGS", null));
    }
}
