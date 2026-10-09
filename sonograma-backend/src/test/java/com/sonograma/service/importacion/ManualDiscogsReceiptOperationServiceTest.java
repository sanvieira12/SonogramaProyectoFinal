package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.ManualDiscogsOperationContextDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.exception.ManualDiscogsDuplicateException;
import com.sonograma.exception.ManualDiscogsPendingFinalizationException;
import com.sonograma.exception.ManualDiscogsFinalizationConfirmationException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.VentaRepository;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.ManualDiscogsImportOperationRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscogsManualBatchExcelService;
import com.sonograma.service.DiscogsManualBatchService;
import com.sonograma.service.DiscogsManualBatchZipService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.DiscoService;
import com.sonograma.service.StockValuationService;
import com.sonograma.service.VentaService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

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
    @Autowired private DiscoService discoService;
    @Autowired private StockValuationService stockValuationService;
    @Autowired private VentaService ventaService;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private com.sonograma.service.ManualDiscogsSourceReconciliationService reconciliationService;
    @Autowired private com.sonograma.repository.ManualDiscogsSourceReconciliationRepository reconciliationRepository;
    @Autowired private com.sonograma.repository.ManualDiscogsFinalizationSnapshotRepository snapshotRepository;

    @MockBean private AudioPreviewService audioPreviewService;

    @BeforeEach
    void clean() {
        detalleVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        clienteRepository.deleteAll();
        snapshotRepository.deleteAll();
        operationRepository.deleteAll();
        copyRepository.deleteAll();
        batchRepository.deleteAll();
        reconciliationRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void sameOperationIsReceivedExactlyOnceAndReplayIsSuccessful() {
        DiscoImportPreviewDTO preview = pendingPreview(456L);

        ManualDiscogsImportResultDTO first = importService.guardar(preview);
        DiscoQrCopy received = copyRepository.findAll().getFirst();
        ManualDiscogsImportResultDTO replay = importService.guardar(preview);

        assertThat(first.getResultType()).isEqualTo("NEW_PRODUCT");
        assertThat(first.isAlreadyProcessed()).isFalse();
        assertThat(replay.getResultType()).isEqualTo("ALREADY_COMPLETED_OPERATION");
        assertThat(replay.isAlreadyProcessed()).isTrue();
        assertThat(replay.getBatchId()).isEqualTo(first.getBatchId());
        assertThat(replay.getCopyIds()).containsExactlyElementsOf(first.getCopyIds());
        assertThat(operationRepository.findById(java.util.UUID.fromString(preview.getOperationId())))
                .get().extracting(com.sonograma.entity.ManualDiscogsImportOperation::getStatus)
                .isEqualTo(com.sonograma.enums.ManualDiscogsImportOperationStatus.COMPLETED);
        assertThat(copyRepository.findAll()).singleElement().satisfies(copy -> {
            assertThat(copy.getId()).isEqualTo(received.getId());
            assertThat(copy.getCodigoQr()).isEqualTo(received.getCodigoQr());
        });
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
        var lineage = operationService.get(java.util.UUID.fromString(preview.getOperationId()));
        assertThat(lineage.normalizedSourceCustomerCode()).isEqualTo("JPH");
        assertThat(lineage.submittedPrice()).isEqualByComparingTo("1500");
        assertThat(lineage.submittedCondition()).isEqualTo("VG+ con detalle escrito");
        assertThat(lineage.batchId()).isEqualTo(first.getBatchId());
        assertThat(lineage.resultProductId()).isEqualTo(first.getProductId());
        assertThat(lineage.resultCopyIds()).containsExactly(received.getId());
        assertThat(operationService.findCreatingOperation(received.getId()).operationId())
                .isEqualTo(java.util.UUID.fromString(preview.getOperationId()));
        var valuation = stockValuationService.current();
        assertThat(valuation.projectedUsedKnownUyu()).isEqualByComparingTo("1500");
        assertThat(valuation.availableUsedCopies()).isEqualTo(1);
        assertThat(valuation.usedAvailableCopiesWithoutPrice()).isZero();
        assertThat(valuation.importedNewEur()).isZero();
        assertThat(valuation.importedNewUyu()).isZero();
    }

    @Test
    void multiCopyReceiptPersistsNormalizedLineageForEveryCreatedCopy() {
        DiscoImportPreviewDTO preview = pendingPreview(460L, "SOURCEA");
        operationRepository.deleteById(java.util.UUID.fromString(preview.getOperationId()));
        preview.setCantidadCopias(2);
        preview.setOperationId(operationService.createPending(460L, 2).toString());

        ManualDiscogsImportResultDTO result = importService.guardar(preview);

        assertThat(result.getCopyIds()).hasSize(2).doesNotHaveDuplicates();
        assertThat(copyRepository.findAll()).hasSize(2)
                .extracting(DiscoQrCopy::getCodigoQr).doesNotHaveDuplicates();
        assertThat(operationService.get(java.util.UUID.fromString(preview.getOperationId())).resultCopyIds())
                .containsExactlyElementsOf(result.getCopyIds());
        result.getCopyIds().forEach(copyId ->
                assertThat(operationService.findCreatingOperation(copyId).operationId())
                        .isEqualTo(java.util.UUID.fromString(preview.getOperationId())));
    }

    @Test
    void differentOperationForSameSourceAndReleaseRequiresExplicitAuditedOverride() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(456L));
        DiscoImportPreviewDTO secondPreview = pendingPreview(456L);
        secondPreview.setCustomerCode(" JPH ");

        assertThatThrownBy(() -> importService.guardar(secondPreview))
                .isInstanceOf(ManualDiscogsDuplicateException.class);
        assertThat(copyRepository.findAll()).hasSize(1);
        assertThat(operationRepository.findById(java.util.UUID.fromString(secondPreview.getOperationId())))
                .get().extracting(com.sonograma.entity.ManualDiscogsImportOperation::getStatus)
                .isEqualTo(ManualDiscogsImportOperationStatus.PENDING);

        secondPreview.setDuplicateOverride(true);
        secondPreview.setDuplicateOverrideReason("Proveedor entregó otra copia física");
        secondPreview.setCopySalePrice(new java.math.BigDecimal("1700"));
        secondPreview.setPhysicalCondition("NM");
        ManualDiscogsImportResultDTO second = importService.guardar(secondPreview);

        assertThat(first.getProductId()).isEqualTo(second.getProductId());
        assertThat(second.getResultType()).isEqualTo("EXISTING_PRODUCT");
        assertThat(discoRepository.findAll()).singleElement().satisfies(disco -> {
            assertThat(disco.getCantidadCopias()).isEqualTo(2);
            assertThat(qrCopyService.countAvailableCopies(disco.getIdDisco())).isEqualTo(2);
        });
        assertThat(batchRepository.findAll()).singleElement().satisfies(batch -> {
            assertThat(batch.getNormalizedCustomerCode()).isEqualTo("JPH");
            assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batch.getId()))
                    .hasSize(2)
                    .extracting(DiscoQrCopy::getCopyNumber, DiscoQrCopy::getCodigoQr)
                    .doesNotHaveDuplicates();
        });
        assertThat(operationRepository.findById(java.util.UUID.fromString(secondPreview.getOperationId())))
                .get().satisfies(operation -> {
                    assertThat(operation.getDuplicateOverride()).isTrue();
                    assertThat(operation.getDuplicateOverrideReason()).isEqualTo("Proveedor entregó otra copia física");
                    assertThat(operation.getSubmittedPrice()).isEqualByComparingTo("1700");
                    assertThat(operation.getSubmittedCondition()).isEqualTo("NM");
                });
    }

    @Test
    void laterServerInteractionReusesSameOpenNormalizedBatchWithoutRecreatingFirstCopy() {
        DiscoImportPreviewDTO firstPreview = pendingPreview(456L, " testsource ");
        importService.guardar(firstPreview);
        DiscogsManualBatch openBatch = batchRepository.findAll().getFirst();
        DiscoQrCopy firstCopy = copyRepository.findAll().getFirst();
        Long firstCopyId = firstCopy.getId();
        String firstQr = firstCopy.getCodigoQr();

        // A new operation models a later browser/session interaction. No browser state is reused.
        DiscoImportPreviewDTO laterPreview = pendingPreview(457L, "TESTSOURCE");
        importService.guardar(laterPreview);

        assertThat(batchRepository.findAll()).singleElement().satisfies(batch -> {
            assertThat(batch.getId()).isEqualTo(openBatch.getId());
            assertThat(batch.getNormalizedCustomerCode()).isEqualTo("TESTSOURCE");
            assertThat(batch.getStatus()).isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.OPEN);
        });
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(openBatch.getId()))
                .hasSize(2)
                .anySatisfy(copy -> {
                    assertThat(copy.getId()).isEqualTo(firstCopyId);
                    assertThat(copy.getCodigoQr()).isEqualTo(firstQr);
                });
        assertThat(copyRepository.count()).isEqualTo(2);
    }

    @Test
    void finalizationChangesBatchStateButDoesNotMutateAnyPhysicalCopyField() {
        importService.guardar(pendingPreview(456L, "TESTSOURCE"));
        importService.guardar(pendingPreview(457L, "testsource"));
        DiscogsManualBatch batch = batchRepository.findAll().getFirst();
        java.util.List<String> before = copySnapshot(batch.getId());

        batchService.finalizeBatch(batch.getId(), new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30, true));

        assertThat(batchRepository.findById(batch.getId())).get().satisfies(finalized -> {
            assertThat(finalized.getStatus()).isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED);
            assertThat(finalized.getFinalizedAt()).isNotNull();
        });
        assertThat(copySnapshot(batch.getId())).containsExactlyElementsOf(before);
        assertThat(copyRepository.count()).isEqualTo(2);
    }

    @Test
    void logicalSourceSpansBatchesAndKeepsPhysicalCountDistinctFromProductCount() {
        importService.guardar(pendingPreview(456L, "TESTSOURCE"));
        importService.guardar(override(pendingPreview(456L, " testsource ")));
        importService.guardar(pendingPreview(457L, "testsource"));
        DiscogsManualBatch firstBatch = batchRepository.findAll().getFirst();
        batchService.finalizeBatch(firstBatch.getId(), new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(25, true));
        importService.guardar(pendingPreview(458L, "TESTSOURCE"));

        assertThat(batchRepository.findAll()).hasSize(2);
        assertThat(copyRepository.findByManualCustomerCodeOrderByCopyNumber("TESTSOURCE")).hasSize(4);
        assertThat(discoRepository.findAll()).hasSize(3);
        assertThat(discoService.listarFuentesImportacionDiscogs())
                .filteredOn(source -> "TESTSOURCE".equals(source.customerCode()))
                .singleElement()
                .satisfies(source -> assertThat(source.productos()).isEqualTo(4));
        assertThat(discoService.obtenerTodos(null, "manual:customer:TESTSOURCE")).hasSize(3);
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
    void concurrentDifferentOperationsForSameSourceAndReleaseCannotSilentlyDuplicate() throws Exception {
        DiscoImportPreviewDTO firstPreview = pendingPreview(9001L, "SOURCEA");
        DiscoImportPreviewDTO secondPreview = pendingPreview(9001L, "SOURCEA");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> receive = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    importService.guardar(Thread.currentThread().getName().endsWith("1") ? firstPreview : secondPreview);
                    return true;
                } catch (ManualDiscogsDuplicateException expected) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(receive);
            Future<Boolean> second = executor.submit(receive);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(copyRepository.findAll()).hasSize(1);
            assertThat(operationRepository.findAll())
                    .extracting(com.sonograma.entity.ManualDiscogsImportOperation::getStatus)
                    .containsExactlyInAnyOrder(ManualDiscogsImportOperationStatus.COMPLETED,
                            ManualDiscogsImportOperationStatus.PENDING);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateHistoryIncludesSoldAndRemovedCopiesWhileAnotherSourceIsIndependent() {
        DiscoImportPreviewDTO first = pendingPreview(7001L, "SOURCEA");
        ManualDiscogsImportResultDTO received = importService.guardar(first);
        DiscoQrCopy historical = copyRepository.findById(received.getCopyIds().getFirst()).orElseThrow();
        historical.setEstado(EstadoCopiaDisco.VENDIDO);
        copyRepository.saveAndFlush(historical);

        assertThatThrownBy(() -> importService.guardar(pendingPreview(7001L, "SOURCEA")))
                .isInstanceOf(ManualDiscogsDuplicateException.class)
                .satisfies(error -> assertThat(((ManualDiscogsDuplicateException) error)
                        .getExistingCopies().getFirst().estado()).isEqualTo("VENDIDO"));

        historical.setEstado(EstadoCopiaDisco.REMOVED);
        copyRepository.saveAndFlush(historical);
        assertThatThrownBy(() -> importService.guardar(pendingPreview(7001L, "SOURCEA")))
                .isInstanceOf(ManualDiscogsDuplicateException.class)
                .satisfies(error -> assertThat(((ManualDiscogsDuplicateException) error)
                        .getExistingCopies().getFirst().estado()).isEqualTo("REMOVED"));

        ManualDiscogsImportResultDTO otherSource = importService.guardar(pendingPreview(7001L, "SOURCEB"));
        assertThat(otherSource.getProductId()).isEqualTo(received.getProductId());
        assertThat(copyRepository.findAll()).hasSize(2);
        assertThat(batchRepository.findAll()).extracting(DiscogsManualBatch::getNormalizedCustomerCode)
                .containsExactlyInAnyOrder("SOURCEA", "SOURCEB");
    }

    @Test
    void overrideRequiresReasonAndPendingCanBeAuditedThenAbandoned() {
        importService.guardar(pendingPreview(8001L, "SOURCEA"));
        DiscoImportPreviewDTO duplicate = pendingPreview(8001L, "SOURCEA");
        duplicate.setDuplicateOverride(true);

        assertThatThrownBy(() -> importService.guardar(duplicate))
                .hasMessageContaining("motivo");
        assertThat(copyRepository.findAll()).hasSize(1);

        DiscoImportPreviewDTO abandonedPreview = pendingPreview(8002L, "SOURCEA");
        java.util.UUID pendingId = java.util.UUID.fromString(abandonedPreview.getOperationId());
        operationService.updateContext(pendingId,
                new ManualDiscogsOperationContextDTO(" sourcea ", new java.math.BigDecimal("990"), " VG "));
        assertThat(operationService.listPending("SOURCEA"))
                .anySatisfy(operation -> {
                    assertThat(operation.operationId()).isEqualTo(pendingId);
                    assertThat(operation.submittedPrice()).isEqualByComparingTo("990");
                    assertThat(operation.submittedCondition()).isEqualTo("VG");
                });
        assertThat(operationService.abandon(pendingId).status()).isEqualTo("ABANDONED");
        assertThat(operationService.listPending("SOURCEA"))
                .extracting(operation -> operation.operationId()).doesNotContain(pendingId);
        assertThatThrownBy(() -> operationService.updateContext(pendingId,
                new ManualDiscogsOperationContextDTO("SOURCEA", null, null)))
                .hasMessageContaining("descartada");
        assertThatThrownBy(() -> importService.guardar(abandonedPreview))
                .hasMessageContaining("descartada");
        assertThat(copyRepository.findAll()).hasSize(1);

        DiscoImportPreviewDTO fabricatedOverride = pendingPreview(8003L, "SOURCEA");
        fabricatedOverride.setDuplicateOverride(true);
        fabricatedOverride.setDuplicateOverrideReason("Intento sin duplicado real");
        assertThatThrownBy(() -> importService.guardar(fabricatedOverride))
                .hasMessageContaining("No se detectó");
        assertThat(copyRepository.findAll()).hasSize(1);
    }

    @Test
    void finalizationWarnsForPendingButExplicitConfirmationRemainsCopyNeutral() {
        importService.guardar(pendingPreview(8101L, "SOURCEA"));
        DiscogsManualBatch batch = batchRepository.findAll().getFirst();
        java.util.List<String> before = copySnapshot(batch.getId());
        java.util.UUID pendingId = operationService.createPending(8102L, 1);
        operationService.updateContext(pendingId,
                new ManualDiscogsOperationContextDTO("SOURCEA", new java.math.BigDecimal("1000"), "NM"));

        assertThatThrownBy(() -> batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30, false)))
                .isInstanceOf(ManualDiscogsFinalizationConfirmationException.class);

        batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30, true));
        assertThat(copySnapshot(batch.getId())).containsExactlyElementsOf(before);
    }

    @Test
    void receiptFailureRollsBackProductCopyBatchAndCompletionButKeepsPendingContext() {
        DiscoImportPreviewDTO preview = pendingPreview(8201L, "SOURCEA");
        doThrow(new RuntimeException("forced enrichment failure"))
                .when(audioPreviewService).guardarDesdeTracks(anyLong(), eq(preview.getTracks()));

        assertThatThrownBy(() -> importService.guardar(preview)).hasMessageContaining("forced enrichment failure");

        assertThat(discoRepository.findAll()).isEmpty();
        assertThat(copyRepository.findAll()).isEmpty();
        assertThat(batchRepository.findAll()).isEmpty();
        assertThat(operationService.get(java.util.UUID.fromString(preview.getOperationId())).status())
                .isEqualTo("PENDING");
        reset(audioPreviewService);
    }

    @Test
    void newlyReceivedCopyIsVisibleInCatalogAndSaleSearchAndCanBeSoldByExactIdentity() {
        DiscoImportPreviewDTO preview = pendingPreview(8301L, "SOURCEA");
        ManualDiscogsImportResultDTO receipt = importService.guardar(preview);
        Long copyId = receipt.getCopyIds().getFirst();
        DiscoQrCopy copy = copyRepository.findById(copyId).orElseThrow();

        assertThat(qrCopyService.listDetailDtos(receipt.getProductId())).singleElement().satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(copyId);
            assertThat(detail.manualBatchId()).isEqualTo(receipt.getBatchId());
            assertThat(detail.normalizedSourceCustomerCode()).isEqualTo("SOURCEA");
            assertThat(detail.precioVenta()).isEqualByComparingTo("1500");
            assertThat(detail.condicionFisica()).isEqualTo("VG+ con detalle escrito");
            assertThat(detail.estado()).isEqualTo("DISPONIBLE");
        });
        assertThat(discoService.buscarParaVenta("SOURCEA", 20)).singleElement().satisfies(product -> {
            assertThat(product.idDisco()).isEqualTo(receipt.getProductId());
            assertThat(product.availableCopies()).singleElement().satisfies(choice -> {
                assertThat(choice.copyId()).isEqualTo(copyId);
                assertThat(choice.codigoQr()).isEqualTo(copy.getCodigoQr());
            });
        });

        Cliente customer = new Cliente();
        customer.setNombre("Phase 5 Sale Customer");
        customer.setCedula("PHASE5-" + System.nanoTime());
        customer.setActivo(true);
        customer = clienteRepository.save(customer);
        var sale = ventaService.registrarVenta(VentaRequestDTO.builder()
                .idCliente(customer.getIdCliente())
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new java.math.BigDecimal("1500"))
                .detalles(java.util.List.of(DetalleVentaDTO.builder()
                        .idDisco(receipt.getProductId())
                        .copyId(copyId)
                        .codigoQr(copy.getCodigoQr())
                        .cantidad(1)
                        .precioUnitario(new java.math.BigDecimal("1500"))
                        .build()))
                .build());

        assertThat(sale.getDetalles()).singleElement().satisfies(detail -> {
            assertThat(detail.getCopyId()).isEqualTo(copyId);
            assertThat(detail.getCopyIds()).containsExactly(copyId);
        });
        assertThat(copyRepository.findById(copyId)).get()
                .extracting(DiscoQrCopy::getEstado).isEqualTo(EstadoCopiaDisco.VENDIDO);
        assertThat(operationService.findCreatingOperation(copyId).operationId())
                .isEqualTo(java.util.UUID.fromString(preview.getOperationId()));
    }

    @Test
    void sourceReconciliationCountsPhysicalHistoryStatesReleasesDuplicatesAndOperationsSeparately() {
        var unknown = reconciliationService.current(" sourcea ");
        assertThat(unknown.expectedCopyCount()).isNull();
        assertThat(unknown.provablePhysicalCopyCount()).isZero();
        assertThat(unknown.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.EXPECTED_COUNT_UNKNOWN);

        importService.guardar(pendingPreview(8401L, "SOURCEA"));
        importService.guardar(override(pendingPreview(8401L, " sourcea ")));
        importService.guardar(pendingPreview(8402L, "SOURCEA"));
        java.util.List<DiscoQrCopy> copies = copyRepository.findByManualCustomerCodeOrderByCopyNumber("SOURCEA");
        copies.get(0).setEstado(EstadoCopiaDisco.VENDIDO);
        copies.get(2).setEstado(EstadoCopiaDisco.REMOVED);
        copyRepository.saveAllAndFlush(copies);

        DiscoImportPreviewDTO pending = pendingPreview(8403L, "SOURCEA");
        operationService.updateContext(java.util.UUID.fromString(pending.getOperationId()),
                new ManualDiscogsOperationContextDTO("SOURCEA", new java.math.BigDecimal("900"), "VG"));
        DiscoImportPreviewDTO abandoned = pendingPreview(8404L, "SOURCEA");
        operationService.updateContext(java.util.UUID.fromString(abandoned.getOperationId()),
                new ManualDiscogsOperationContextDTO("SOURCEA", null, null));
        operationService.abandon(java.util.UUID.fromString(abandoned.getOperationId()));

        var expectedFive = reconciliationService.updateExpectedCount(" SOURCEA ",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                        5, null, "Conteo inicial", null));
        assertThat(expectedFive.normalizedSourceCustomerCode()).isEqualTo("SOURCEA");
        assertThat(expectedFive.provablePhysicalCopyCount()).isEqualTo(3);
        assertThat(expectedFive.availableCopyCount()).isEqualTo(1);
        assertThat(expectedFive.soldCopyCount()).isEqualTo(1);
        assertThat(expectedFive.removedCopyCount()).isEqualTo(1);
        assertThat(expectedFive.distinctReleaseCount()).isEqualTo(2);
        assertThat(expectedFive.duplicateReleaseGroupCount()).isEqualTo(1);
        assertThat(expectedFive.extraDuplicateCopyCount()).isEqualTo(1);
        assertThat(expectedFive.pendingOperationCount()).isEqualTo(1);
        assertThat(expectedFive.completedOperationCount()).isEqualTo(3);
        assertThat(expectedFive.abandonedOperationCount()).isEqualTo(1);
        assertThat(expectedFive.difference()).isEqualTo(-2);
        assertThat(expectedFive.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.IN_PROGRESS);

        operationService.abandon(java.util.UUID.fromString(pending.getOperationId()));
        var difference = reconciliationService.current("sourcea");
        assertThat(difference.difference()).isEqualTo(-2);
        assertThat(difference.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.DIFFERENCE);

        var matched = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                        3, difference.version(), "Conteo confirmado", "Se corrigió el total esperado"));
        assertThat(matched.difference()).isZero();
        assertThat(matched.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.MATCHED);
        assertThat(matched.expectedCountChangeReason()).isEqualTo("Se corrigió el total esperado");
        assertThat(matched.updatedBy()).isNotBlank();

        var extra = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                        1, matched.version(), matched.reconciliationNote(), "Reconteo de control"));
        assertThat(extra.difference()).isEqualTo(2);
        assertThat(extra.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.DIFFERENCE);
    }

    @Test
    void expectedCountUpdatesUseVersionToPreventSilentConcurrentOverwrite() throws Exception {
        var initial = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(10, null, null, null));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> firstUpdate = () -> {
                ready.countDown(); start.await(5, TimeUnit.SECONDS);
                try {
                    reconciliationService.updateExpectedCount("SOURCEA",
                            new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                                    11, initial.version(), null, "Primer ajuste"));
                    return true;
                } catch (com.sonograma.exception.ConflictoNegocioException expected) { return false; }
            };
            java.util.concurrent.Callable<Boolean> secondUpdate = () -> {
                ready.countDown(); start.await(5, TimeUnit.SECONDS);
                try {
                    reconciliationService.updateExpectedCount(" sourcea ",
                            new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                                    12, initial.version(), null, "Segundo ajuste"));
                    return true;
                } catch (com.sonograma.exception.ConflictoNegocioException expected) { return false; }
            };
            Future<Boolean> first = executor.submit(firstUpdate);
            Future<Boolean> second = executor.submit(secondUpdate);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(reconciliationRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void finalizationRequiresOneCombinedConfirmationAndPersistsNoSnapshotWhenRejected() {
        importService.guardar(pendingPreview(8501L, "SOURCEA"));
        DiscogsManualBatch batch = batchRepository.findAll().getFirst();
        var expected = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(3, null, null, null));
        java.util.UUID pendingId = operationService.createPending(8502L, 1);
        operationService.updateContext(pendingId,
                new ManualDiscogsOperationContextDTO("SOURCEA", null, null));

        assertThatThrownBy(() -> batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30, false)))
                .isInstanceOf(ManualDiscogsFinalizationConfirmationException.class)
                .satisfies(error -> {
                    var conflict = (ManualDiscogsFinalizationConfirmationException) error;
                    assertThat(conflict.getWarnings()).containsExactly(
                            "La cantidad registrada no coincide con la esperada.",
                            "Hay 1 importación pendiente.");
                    assertThat(conflict.getReconciliation().difference()).isEqualTo(-2);
                    assertThat(conflict.getReconciliation().pendingOperationCount()).isEqualTo(1);
                });
        assertThat(batchRepository.findById(batch.getId())).get()
                .extracting(DiscogsManualBatch::getStatus)
                .isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.OPEN);
        assertThat(snapshotRepository.count()).isZero();

        java.util.List<String> before = copySnapshot(batch.getId());
        batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30, true));
        assertThat(copySnapshot(batch.getId())).containsExactlyElementsOf(before);
        assertThat(snapshotRepository.findByManualBatchId(batch.getId())).get().satisfies(snapshot -> {
            assertThat(snapshot.getExpectedCopyCount()).isEqualTo(expected.expectedCopyCount());
            assertThat(snapshot.getProvablePhysicalCopyCount()).isEqualTo(1);
            assertThat(snapshot.getPendingOperationCount()).isEqualTo(1);
            assertThat(snapshot.getDifference()).isEqualTo(-2);
            assertThat(snapshot.getReconciliationStatus())
                    .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.IN_PROGRESS);
        });
    }

    @Test
    void finalizationWithUnknownExpectedCountWarnsButCanExplicitlyProceed() {
        importService.guardar(pendingPreview(8551L, "SOURCEB"));
        DiscogsManualBatch batch = batchRepository.findAll().getFirst();

        assertThatThrownBy(() -> batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(25)))
                .isInstanceOf(ManualDiscogsFinalizationConfirmationException.class)
                .satisfies(error -> assertThat(((ManualDiscogsFinalizationConfirmationException) error)
                        .getWarnings()).containsExactly("No se definió una cantidad esperada."));
        assertThat(snapshotRepository.count()).isZero();

        batchService.finalizeBatch(batch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(25, true));
        assertThat(snapshotRepository.findByManualBatchId(batch.getId())).get()
                .extracting(com.sonograma.entity.ManualDiscogsFinalizationSnapshot::getReconciliationStatus)
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.EXPECTED_COUNT_UNKNOWN);
    }

    @Test
    void snapshotsStayImmutableAcrossStateChangesAndLaterTechnicalBatches() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(8601L, "SOURCEA"));
        var reconciliation = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(1, null, null, null));
        DiscogsManualBatch firstBatch = batchRepository.findAll().getFirst();
        batchService.finalizeBatch(firstBatch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30));
        var firstSnapshot = snapshotRepository.findByManualBatchId(firstBatch.getId()).orElseThrow();
        assertThat(firstSnapshot.getAvailableCopyCount()).isEqualTo(1);
        assertThat(firstSnapshot.getSoldCopyCount()).isZero();

        DiscoQrCopy firstCopy = copyRepository.findById(first.getCopyIds().getFirst()).orElseThrow();
        firstCopy.setEstado(EstadoCopiaDisco.VENDIDO);
        copyRepository.saveAndFlush(firstCopy);
        ManualDiscogsImportResultDTO second = importService.guardar(pendingPreview(8602L, "sourcea"));
        var current = reconciliationService.current("SOURCEA");
        assertThat(current.provablePhysicalCopyCount()).isEqualTo(2);
        assertThat(current.availableCopyCount()).isEqualTo(1);
        assertThat(current.soldCopyCount()).isEqualTo(1);
        assertThat(snapshotRepository.findById(firstSnapshot.getId())).get().satisfies(snapshot -> {
            assertThat(snapshot.getProvablePhysicalCopyCount()).isEqualTo(1);
            assertThat(snapshot.getAvailableCopyCount()).isEqualTo(1);
            assertThat(snapshot.getSoldCopyCount()).isZero();
        });

        var expectedTwo = reconciliationService.updateExpectedCount("SOURCEA",
                new com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO(
                        2, reconciliation.version(), null, "Se recibió una copia posterior"));
        DiscogsManualBatch secondBatch = batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == com.sonograma.enums.DiscogsManualBatchStatus.OPEN)
                .findFirst().orElseThrow();
        batchService.finalizeBatch(secondBatch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(30));
        assertThat(snapshotRepository.findAll()).hasSize(2);
        assertThat(reconciliationService.snapshots("SOURCEA"))
                .extracting(com.sonograma.dto.ManualDiscogsFinalizationSnapshotDTO::batchId)
                .containsExactly(secondBatch.getId(), firstBatch.getId());

        DiscoQrCopy secondCopy = copyRepository.findById(second.getCopyIds().getFirst()).orElseThrow();
        secondCopy.setEstado(EstadoCopiaDisco.REMOVED);
        copyRepository.saveAndFlush(secondCopy);
        assertThat(reconciliationService.current("SOURCEA").removedCopyCount()).isEqualTo(1);
        assertThat(snapshotRepository.findByManualBatchId(secondBatch.getId())).get()
                .extracting(com.sonograma.entity.ManualDiscogsFinalizationSnapshot::getRemovedCopyCount)
                .isEqualTo(0L);
        assertThat(expectedTwo.reconciliationStatus())
                .isEqualTo(com.sonograma.enums.ManualDiscogsReconciliationStatus.MATCHED);
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
    void manualReceiptPreservesFullDiscogsUrlForCustomerExport() {
        String fullUrl = "https://www.discogs.com/es/release/456-ZP-Tracid";
        DiscoImportPreviewDTO preview = pendingPreview(456L);
        preview.setDiscogsUrl(fullUrl);

        ManualDiscogsImportResultDTO result = importService.guardar(preview);

        assertThat(discoRepository.findById(result.getProductId())).get()
                .extracting(Disco::getDiscogsUrl)
                .isEqualTo(fullUrl);
    }

    @Test
    void finalizingBatchMakesNextSameCustomerImportCreateANewBatch() {
        ManualDiscogsImportResultDTO first = importService.guardar(pendingPreview(456L));
        DiscogsManualBatch oldBatch = batchRepository.findAll().getFirst();
        java.util.List<Long> oldCopyIds = copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(oldBatch.getId())
                .stream().map(com.sonograma.entity.DiscoQrCopy::getId).toList();

        DiscogsManualBatchService.FinalizedBatch finalized = batchService.finalizeBatch(oldBatch.getId(),
                new com.sonograma.dto.DiscogsManualBatchFinalizeRequestDTO(25, true));
        assertThat(finalized.status()).isEqualTo(com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED);

        ManualDiscogsImportResultDTO second = importService.guardar(override(pendingPreview(456L)));

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
        assertThat(newBatch.getNormalizedCustomerCode()).isEqualTo(oldBatch.getNormalizedCustomerCode());
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(newBatch.getId())).hasSize(1);
        assertThat(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(newBatch.getId()).getFirst().getId())
                .isNotIn(oldCopyIds);

        when(audioPreviewService.listarPorDisco(anyLong())).thenReturn(java.util.List.of());
        assertThat(excelService.generate(oldBatch.getId()).content()).isNotEmpty();
        assertThat(zipService.generate(oldBatch.getId()).content()).isNotEmpty();
    }

    private DiscoImportPreviewDTO pendingPreview(long releaseId) {
        return pendingPreview(releaseId, " jPh ");
    }

    private DiscoImportPreviewDTO pendingPreview(long releaseId, String customerCode) {
        DiscoImportPreviewDTO preview = DiscoImportPreviewDTO.builder()
                .artista("Artist")
                .album("Album")
                .discogsReleaseId(releaseId)
                .discogsUrl("https://www.discogs.com/release/" + releaseId)
                .cantidadCopias(1)
                .condicion(CondicionDisco.USADO.name())
                .copySalePrice(new java.math.BigDecimal("1500"))
                .physicalCondition("  VG+ con detalle escrito  ")
                .customerCode(customerCode)
                .formato(TipoDisco.VINILO.name())
                .procedencia("DISCOGS")
                .errores(new ArrayList<>())
                .build();
        preview.setOperationId(operationService.createPending(releaseId, 1).toString());
        return preview;
    }

    private DiscoImportPreviewDTO override(DiscoImportPreviewDTO preview) {
        preview.setDuplicateOverride(true);
        preview.setDuplicateOverrideReason("Proveedor entregó otra copia física");
        return preview;
    }

    private java.util.List<String> copySnapshot(Long batchId) {
        return copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batchId).stream()
                .map(copy -> String.join("|",
                        String.valueOf(copy.getId()),
                        String.valueOf(copy.getIdDisco()),
                        String.valueOf(copy.getCopyNumber()),
                        copy.getCodigoQr(),
                        String.valueOf(copy.getManualDiscogsBatch().getId()),
                        copy.getEstado().name(),
                        String.valueOf(copy.getPrecioVenta()),
                        String.valueOf(copy.getCondicionFisica())))
                .toList();
    }
}
