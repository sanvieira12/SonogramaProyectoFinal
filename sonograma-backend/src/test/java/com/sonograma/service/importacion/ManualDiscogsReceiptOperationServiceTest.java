package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.repository.ManualDiscogsImportOperationRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscogsManualBatchExcelService;
import com.sonograma.service.DiscogsManualBatchService;
import com.sonograma.service.DiscogsManualBatchZipService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("dev")
class ManualDiscogsReceiptOperationServiceTest {

    @Autowired private DiscogsImportService importService;
    @Autowired private ManualDiscogsReceiptOperationService operationService;
    @Autowired private ManualDiscogsImportOperationRepository operationRepository;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private DiscogsManualBatchRepository batchRepository;
    @Autowired private DiscogsManualBatchService batchService;
    @Autowired private DiscogsManualBatchExcelService excelService;
    @Autowired private DiscogsManualBatchZipService zipService;
    @Autowired private DiscoQrCopyService qrCopyService;

    @MockBean private AudioPreviewService audioPreviewService;

    @BeforeEach
    void clean() {
        operationRepository.deleteAll();
        copyRepository.deleteAll();
        batchRepository.deleteAll();
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
        assertThat(batchRepository.findAll()).singleElement()
                .satisfies(batch -> {
                    assertThat(batch.getNormalizedCustomerCode()).isEqualTo("JPH");
                    assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batch.getId()))
                            .singleElement()
                            .satisfies(copy -> {
                                assertThat(copy.getPrecioVenta()).isEqualByComparingTo("1500");
                                assertThat(copy.getCondicionFisica()).isEqualTo("VG+ con detalle escrito");
                            });
                });
    }

    @Test
    void differentOperationsForTheSameReleaseAddTwoLegitimateCopies() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(456L));
        DiscoImportPreviewDTO secondPreview = pendingPreview(456L);
        secondPreview.setCustomerCode(" JPH ");
        ManualDiscogsImportResultDTO second = importService.guardar(secondPreview);

        assertThat(first.getProductId()).isEqualTo(second.getProductId());
        assertThat(second.getResultType()).isEqualTo("EXISTING_PRODUCT");
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(2);
        });
        assertThat(batchRepository.findAll()).singleElement().satisfies(batch -> {
            assertThat(batch.getNormalizedCustomerCode()).isEqualTo("JPH");
            assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batch.getId())).hasSize(2);
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

    @Test
    void manualReceiptKeepsCoverAndPassesYoutubeTracksToTheExistingEnrichmentStore() {
        DiscoImportPreviewDTO preview = pendingPreview(456L);
        preview.setImagenUrl("https://cdn.example/cover.jpg");
        preview.setTracks(java.util.List.of(
                new TrackInfo("A1", "Track", null, "https://youtube.example/track")
        ));

        importService.guardar(preview);

        assertThat(discoRepository.findAll()).singleElement()
                .extracting(Disco::getImagenUrl)
                .isEqualTo("https://cdn.example/cover.jpg");
        verify(audioPreviewService).guardarDesdeTracks(anyLong(), eq(preview.getTracks()));
    }

    @Test
    void finalizingBatchMakesNextSameCustomerImportCreateANewBatch() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(456L));
        DiscogsManualBatch oldBatch = batchRepository.findAll().getFirst();
        java.util.List<Long> oldCopyIds = copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(oldBatch.getId())
                .stream().map(com.sonograma.entity.DiscoQrCopy::getId).toList();

        DiscogsManualBatchService.FinalizedBatch finalized = batchService.finalizeBatch(oldBatch.getId());
        assertThat(finalized.status()).isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED);

        ManualDiscogsImportResultDTO second = importService.guardar(pendingPreview(456L));

        assertThat(second.getProductId()).isEqualTo(first.getProductId());
        assertThat(batchRepository.findAll()).hasSize(2);
        assertThat(batchRepository.findAll()).filteredOn(batch ->
                batch.getStatus() == com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED)
                .singleElement().satisfies(batch -> {
                    assertThat(batch.getId()).isEqualTo(oldBatch.getId());
                    assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batch.getId()))
                            .extracting(com.sonograma.entity.DiscoQrCopy::getId)
                            .containsExactlyElementsOf(oldCopyIds);
                });
        DiscogsManualBatch newBatch = batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == com.sonograma.enums.DiscogsManualBatchStatus.OPEN)
                .findFirst().orElseThrow();
        assertThat(newBatch.getId()).isNotEqualTo(oldBatch.getId());
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(newBatch.getId())).hasSize(1);
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(newBatch.getId()).getFirst().getId())
                .isNotIn(oldCopyIds);

        when(audioPreviewService.listarPorDisco(anyLong())).thenReturn(java.util.List.of());
        assertThat(excelService.generate(oldBatch.getId()).content()).isNotEmpty();
        assertThat(zipService.generate(oldBatch.getId()).content()).isNotEmpty();
    }

    private DiscoImportPreviewDTO pendingPreview(long releaseId) {
        DiscoImportPreviewDTO preview = DiscoImportPreviewDTO.builder()
                .artista("Artist")
                .album("Album")
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .cantidadCopias(1)
                .condicion(CondicionDisco.USADO.name())
                .copySalePrice(new java.math.BigDecimal("1500"))
                .physicalCondition("  VG+ con detalle escrito  ")
                .customerCode(" jPh ")
                .formato(TipoDisco.VINILO.name())
                .procedencia("DISCOGS")
                .errores(new ArrayList<>())
                .build();
        preview.setOperationId(operationService.createPending(releaseId, 1).toString());
        return preview;
    }
}
