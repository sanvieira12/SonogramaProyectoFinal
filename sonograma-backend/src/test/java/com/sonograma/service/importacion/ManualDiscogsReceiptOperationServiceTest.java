package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.ManualDiscogsImportOperationRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
class ManualDiscogsReceiptOperationServiceTest {

    @Autowired private DiscogsImportService importService;
    @Autowired private ManualDiscogsReceiptOperationService operationService;
    @Autowired private ManualDiscogsImportOperationRepository operationRepository;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private DiscoQrCopyService qrCopyService;

    @MockBean private AudioPreviewService audioPreviewService;

    @BeforeEach
    void clean() {
        operationRepository.deleteAll();
        copyRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void sameOperationIsReceivedExactlyOnceAndReplayIsSuccessful() {
        DiscoImportPreviewDTO preview = pendingPreview(456L);

        ManualDiscogsImportResultDTO first = importService.guardar(preview);
        ManualDiscogsImportResultDTO replay = importService.guardar(preview);

        assertThat(first.getResultType()).isEqualTo("NEW_PRODUCT");
        assertThat(first.isAlreadyProcessed()).isFalse();
        assertThat(replay.getResultType()).isEqualTo("ALREADY_COMPLETED_OPERATION");
        assertThat(replay.isAlreadyProcessed()).isTrue();
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(1);
            assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(1);
        });
    }

    @Test
    void differentOperationsForTheSameReleaseAddTwoLegitimateCopies() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(456L));
        ManualDiscogsImportResultDTO second = importService.guardar(pendingPreview(456L));

        assertThat(first.getProductId()).isEqualTo(second.getProductId());
        assertThat(second.getResultType()).isEqualTo("EXISTING_PRODUCT");
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(2);
        });
    }

    @Test
    void concurrentSubmissionsOfTheSameOperationReceiveOnlyOneCopy() throws Exception {
        DiscoImportPreviewDTO preview = pendingPreview(456L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<ManualDiscogsImportResultDTO> confirm = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return importService.guardar(preview);
            };
            Future<ManualDiscogsImportResultDTO> first = executor.submit(confirm);
            Future<ManualDiscogsImportResultDTO> second = executor.submit(confirm);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).isAlreadyProcessed()
                    || second.get(10, TimeUnit.SECONDS).isAlreadyProcessed()).isTrue();
            assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
                assertThat(disco.getCantidadCopias()).isEqualTo(1);
                assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(1);
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidMetadataCannotCompleteOrCreateStock() {
        DiscoImportPreviewDTO preview = pendingPreview(456L);
        preview.setErrores(new ArrayList<>(java.util.List.of("No se pudo obtener metadata válida")));

        assertThatThrownBy(() -> importService.guardar(preview))
                .hasMessageContaining("metadata incompleta");
        assertThat(discoRepository.findAll()).isEmpty();
        assertThat(copyRepository.findAll()).isEmpty();
    }

    private DiscoImportPreviewDTO pendingPreview(long releaseId) {
        DiscoImportPreviewDTO preview = DiscoImportPreviewDTO.builder()
                .artista("Artist")
                .album("Album")
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .cantidadCopias(1)
                .condicion(CondicionDisco.USADO.name())
                .formato(TipoDisco.VINILO.name())
                .procedencia("DISCOGS")
                .errores(new ArrayList<>())
                .build();
        preview.setOperationId(operationService.createPending(releaseId, 1).toString());
        return preview;
    }
}
