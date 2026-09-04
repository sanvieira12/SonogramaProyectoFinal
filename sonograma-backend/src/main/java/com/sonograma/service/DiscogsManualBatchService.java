package com.sonograma.service;

import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Foundation services for persistent manual Discogs customer batches.
 * No manual import flow calls this service yet; Phase 2 will connect it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiscogsManualBatchService {

    private static final Object[] CUSTOMER_LOCKS = new Object[64];

    static {
        for (int i = 0; i < CUSTOMER_LOCKS.length; i++) CUSTOMER_LOCKS[i] = new Object();
    }

    private final DiscogsManualBatchRepository batchRepository;
    private final DiscoQrCopyRepository copyRepository;

    public static String normalizeCustomerCode(String customerCode) {
        if (customerCode == null) {
            throw new IllegalArgumentException("El código de cliente es obligatorio.");
        }
        String normalized = customerCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El código de cliente es obligatorio.");
        }
        return normalized;
    }

    public DiscogsManualBatch createOpenBatch(String customerCode) {
        String normalized = normalizeCustomerCode(customerCode);
        synchronized (lockFor(normalized)) {
            if (batchRepository.existsByNormalizedCustomerCodeAndStatus(
                    normalized, DiscogsManualBatchStatus.OPEN)) {
                throw new ConflictoNegocioException(
                        "Ya existe un batch Discogs abierto para el cliente " + normalized + ".");
            }
            return saveOpenBatch(customerCode.trim(), normalized);
        }
    }

    public DiscogsManualBatch findOrCreateOpenBatch(String customerCode) {
        String normalized = normalizeCustomerCode(customerCode);
        synchronized (lockFor(normalized)) {
            Optional<DiscogsManualBatch> existing = batchRepository.findByNormalizedCustomerCodeAndStatusForUpdate(
                    normalized, DiscogsManualBatchStatus.OPEN);
            return existing.orElseGet(() -> saveOpenBatch(customerCode.trim(), normalized));
        }
    }

    public DiscogsManualBatch assignCopyToOpenBatch(
            String customerCode,
            DiscoQrCopy copy,
            BigDecimal salePrice,
            String physicalCondition
    ) {
        if (copy == null || copy.getId() == null) {
            throw new IllegalArgumentException("La copia física recibida es obligatoria.");
        }
        DiscogsManualBatch batch = findOrCreateOpenBatch(customerCode);
        copy.setManualDiscogsBatch(batch);
        copy.setPrecioVenta(salePrice);
        copy.setCondicionFisica(trimCondition(physicalCondition));
        copyRepository.save(copy);
        return batch;
    }

    private DiscogsManualBatch saveOpenBatch(String customerCode, String normalized) {
        LocalDateTime now = LocalDateTime.now();
        return batchRepository.save(DiscogsManualBatch.builder()
                .customerCode(customerCode)
                .normalizedCustomerCode(normalized)
                .status(DiscogsManualBatchStatus.OPEN)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Object lockFor(String normalizedCustomerCode) {
        return CUSTOMER_LOCKS[Math.floorMod(normalizedCustomerCode.hashCode(), CUSTOMER_LOCKS.length)];
    }

    private String trimCondition(String physicalCondition) {
        if (physicalCondition == null) return null;
        String trimmed = physicalCondition.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Optional<DiscogsManualBatch> findOpenByCustomerCode(String customerCode) {
        return batchRepository.findByNormalizedCustomerCodeAndStatus(
                normalizeCustomerCode(customerCode), DiscogsManualBatchStatus.OPEN);
    }

    public boolean openBatchExists(String customerCode) {
        return batchRepository.existsByNormalizedCustomerCodeAndStatus(
                normalizeCustomerCode(customerCode), DiscogsManualBatchStatus.OPEN);
    }

    public FinalizedBatch finalizeBatch(Long batchId) {
        if (batchId == null || batchId <= 0) {
            throw new com.sonograma.exception.NegocioException("El batch Discogs no es válido.");
        }
        DiscogsManualBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new com.sonograma.exception.RecursoNoEncontradoException(
                        "Batch Discogs", batchId));
        if (batch.getStatus() != DiscogsManualBatchStatus.OPEN) {
            throw new ConflictoNegocioException("El batch Discogs ya está finalizado.");
        }

        LocalDateTime finalizedAt = LocalDateTime.now();
        batch.setStatus(DiscogsManualBatchStatus.FINALIZED);
        batch.setFinalizedAt(finalizedAt);
        batchRepository.save(batch);
        return new FinalizedBatch(batch.getId(), batch.getStatus(), batch.getFinalizedAt());
    }

    @Transactional(readOnly = true)
    public Optional<DiscogsManualBatch> findById(Long batchId) {
        return batchRepository.findById(batchId);
    }

    @Transactional(readOnly = true)
    public List<DiscoQrCopy> findCopiesByBatchId(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("El batch es obligatorio.");
        }
        return copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batchId);
    }

    public record FinalizedBatch(
            Long batchId,
            DiscogsManualBatchStatus status,
            LocalDateTime finalizedAt
    ) {}
}
