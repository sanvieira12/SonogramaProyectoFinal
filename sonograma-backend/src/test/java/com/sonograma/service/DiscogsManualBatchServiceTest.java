package com.sonograma.service;

import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscogsManualBatchServiceTest {

    @Mock
    private DiscogsManualBatchRepository batchRepository;

    @Mock
    private DiscoQrCopyRepository copyRepository;

    private DiscogsManualBatchService service;

    @BeforeEach
    void setUp() {
        service = new DiscogsManualBatchService(batchRepository, copyRepository);
    }

    @Test
    void normalizesCustomerCodeDeterministicallyWithoutFixedLength() {
        assertEquals("JPH-2026-01", DiscogsManualBatchService.normalizeCustomerCode("  jPh-2026-01  "));
        assertEquals("CLIENTE CON ESPACIOS", DiscogsManualBatchService.normalizeCustomerCode("cliente con espacios"));
        assertThrows(IllegalArgumentException.class,
                () -> DiscogsManualBatchService.normalizeCustomerCode("   "));
    }

    @Test
    void createsOpenBatchAndStoresNormalizedCode() {
        when(batchRepository.existsByNormalizedCustomerCodeAndStatus(
                "JPH", DiscogsManualBatchStatus.OPEN)).thenReturn(false);
        when(batchRepository.save(any(DiscogsManualBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DiscogsManualBatch batch = service.createOpenBatch(" jph ");

        ArgumentCaptor<DiscogsManualBatch> captor = ArgumentCaptor.forClass(DiscogsManualBatch.class);
        verify(batchRepository).save(captor.capture());
        DiscogsManualBatch saved = captor.getValue();
        assertEquals("jph", saved.getCustomerCode());
        assertEquals("JPH", saved.getNormalizedCustomerCode());
        assertEquals(DiscogsManualBatchStatus.OPEN, saved.getStatus());
        assertNotNull(saved.getStartedAt());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertSame(saved, batch);
    }

    @Test
    void rejectsSecondOpenBatchForSameNormalizedCode() {
        when(batchRepository.existsByNormalizedCustomerCodeAndStatus(
                "JPH", DiscogsManualBatchStatus.OPEN)).thenReturn(true);

        assertThrows(ConflictoNegocioException.class, () -> service.createOpenBatch("JPH"));
        verify(batchRepository, never()).save(any());
    }

    @Test
    void looksUpOpenBatchCaseAndWhitespaceInsensitively() {
        DiscogsManualBatch batch = DiscogsManualBatch.builder()
                .id(8L).customerCode("jph").normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.OPEN).build();
        when(batchRepository.findByNormalizedCustomerCodeAndStatus(
                "JPH", DiscogsManualBatchStatus.OPEN)).thenReturn(Optional.of(batch));
        when(batchRepository.existsByNormalizedCustomerCodeAndStatus(
                "JPH", DiscogsManualBatchStatus.OPEN)).thenReturn(true);

        assertSame(batch, service.findOpenByCustomerCode("  JpH ").orElseThrow());
        assertTrue(service.openBatchExists("jph"));
        verify(batchRepository).existsByNormalizedCustomerCodeAndStatus("JPH", DiscogsManualBatchStatus.OPEN);
    }

    @Test
    void retrievesOnlyCopiesAssignedToTheRequestedBatch() {
        DiscoQrCopy assigned = DiscoQrCopy.builder().id(4L).idDisco(10L).copyNumber(2)
                .codigoQr("copy-2").precioVenta(new BigDecimal("1450.25"))
                .condicionFisica("VG+ con funda interior genérica y detalle escrito")
                .build();
        when(copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(8L))
                .thenReturn(List.of(assigned));

        List<DiscoQrCopy> result = service.findCopiesByBatchId(8L);

        assertEquals(List.of(assigned), result);
        assertEquals(new BigDecimal("1450.25"), result.getFirst().getPrecioVenta());
        assertEquals("VG+ con funda interior genérica y detalle escrito", result.getFirst().getCondicionFisica());
        verify(copyRepository).findByManualDiscogsBatchIdOrderByCopyNumber(8L);
    }

    @Test
    void finalizesOpenBatchWithoutChangingItsMembershipOrTimestamps() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 10, 1);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 2, 10, 1);
        DiscoQrCopy copy = DiscoQrCopy.builder().id(71L).idDisco(15L).copyNumber(1)
                .codigoQr("batch-copy").build();
        DiscogsManualBatch batch = DiscogsManualBatch.builder()
                .id(15L).customerCode("jph").normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.OPEN)
                .startedAt(startedAt).createdAt(createdAt).updatedAt(updatedAt)
                .copies(List.of(copy)).build();
        when(batchRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(DiscogsManualBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DiscogsManualBatchService.FinalizedBatch result = service.finalizeBatch(15L);

        assertEquals(DiscogsManualBatchStatus.FINALIZED, result.status());
        assertNotNull(result.finalizedAt());
        assertEquals(startedAt, batch.getStartedAt());
        assertEquals(createdAt, batch.getCreatedAt());
        assertSame(copy, batch.getCopies().getFirst());
        verify(batchRepository).save(batch);
    }

    @Test
    void rejectsMissingAndRepeatedFinalization() {
        DiscogsManualBatch finalized = DiscogsManualBatch.builder()
                .id(16L).customerCode("JPH").normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.FINALIZED)
                .finalizedAt(LocalDateTime.of(2026, 9, 3, 10, 0)).build();
        when(batchRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(finalized));

        assertThrows(ConflictoNegocioException.class, () -> service.finalizeBatch(16L));
        assertThrows(com.sonograma.exception.RecursoNoEncontradoException.class,
                () -> service.finalizeBatch(17L));
        verify(batchRepository, never()).save(any(DiscogsManualBatch.class));
    }

    @Test
    void createsNewOpenBatchAfterThePreviousBatchIsFinalized() {
        DiscogsManualBatch oldBatch = DiscogsManualBatch.builder()
                .id(18L).customerCode("JPH").normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.FINALIZED).build();
        DiscogsManualBatch newBatch = DiscogsManualBatch.builder()
                .id(19L).customerCode("JPH").normalizedCustomerCode("JPH")
                .status(DiscogsManualBatchStatus.OPEN).build();
        DiscoQrCopy copy = DiscoQrCopy.builder().id(72L).idDisco(16L).copyNumber(1)
                .codigoQr("new-batch-copy").build();
        when(batchRepository.findByNormalizedCustomerCodeAndStatusForUpdate(
                "JPH", DiscogsManualBatchStatus.OPEN)).thenReturn(Optional.empty());
        when(batchRepository.save(any(DiscogsManualBatch.class))).thenReturn(newBatch);

        DiscogsManualBatch assigned = service.assignCopyToOpenBatch(
                "jph", copy, new BigDecimal("1200"), "VG+");

        assertSame(newBatch, assigned);
        assertEquals(DiscogsManualBatchStatus.OPEN, newBatch.getStatus());
        assertSame(newBatch, copy.getManualDiscogsBatch());
        assertEquals("JPH", oldBatch.getNormalizedCustomerCode());
        assertEquals(DiscogsManualBatchStatus.FINALIZED, oldBatch.getStatus());
        verify(copyRepository).save(copy);
    }
}
