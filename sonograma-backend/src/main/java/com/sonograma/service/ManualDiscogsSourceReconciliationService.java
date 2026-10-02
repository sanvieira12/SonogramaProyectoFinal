package com.sonograma.service;

import com.sonograma.dto.ManualDiscogsExpectedCountRequestDTO;
import com.sonograma.dto.ManualDiscogsFinalizationSnapshotDTO;
import com.sonograma.dto.ManualDiscogsSourceReconciliationDTO;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.entity.ManualDiscogsFinalizationSnapshot;
import com.sonograma.entity.ManualDiscogsSourceReconciliation;
import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import com.sonograma.enums.ManualDiscogsReconciliationStatus;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.*;
import com.sonograma.service.importacion.ManualDiscogsReceiptLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ManualDiscogsSourceReconciliationService {

    public static final int MAX_EXPECTED_COPY_COUNT = 1_000_000;

    private final ManualDiscogsSourceReconciliationRepository reconciliationRepository;
    private final ManualDiscogsFinalizationSnapshotRepository snapshotRepository;
    private final DiscoQrCopyRepository copyRepository;
    private final ManualDiscogsImportOperationRepository operationRepository;
    private final ManualDiscogsReceiptLockService sourceLockService;

    @Transactional(readOnly = true)
    public ManualDiscogsSourceReconciliationDTO current(String sourceCustomerCode) {
        String normalized = DiscogsManualBatchService.normalizeCustomerCode(sourceCustomerCode);
        ManualDiscogsSourceReconciliation record = reconciliationRepository
                .findByNormalizedSourceCustomerCode(normalized).orElse(null);
        return calculate(record == null ? normalized : record.getSourceCustomerCode(), normalized, record);
    }

    @Transactional
    public ManualDiscogsSourceReconciliationDTO updateExpectedCount(
            String sourceCustomerCode,
            ManualDiscogsExpectedCountRequestDTO request) {
        String normalized = DiscogsManualBatchService.normalizeCustomerCode(sourceCustomerCode);
        if (request == null || request.expectedCopyCount() == null
                || request.expectedCopyCount() < 0
                || request.expectedCopyCount() > MAX_EXPECTED_COPY_COUNT) {
            throw new NegocioException("La cantidad esperada debe ser un entero entre 0 y "
                    + MAX_EXPECTED_COPY_COUNT + ".");
        }

        sourceLockService.acquire(normalized);
        ManualDiscogsSourceReconciliation record = reconciliationRepository
                .findByNormalizedSourceCustomerCodeForUpdate(normalized)
                .orElse(null);
        if (record == null) {
            if (request.version() != null) {
                throw new ConflictoNegocioException("La conciliación cambió. Actualizá los datos e intentá nuevamente.");
            }
            record = ManualDiscogsSourceReconciliation.builder()
                    .sourceCustomerCode(sourceCustomerCode.trim())
                    .normalizedSourceCustomerCode(normalized)
                    .expectedCopyCount(request.expectedCopyCount())
                    .note(trimToNull(request.note()))
                    .expectedCountChangeReason(trimToNull(request.changeReason()))
                    .updatedBy(currentActor())
                    .build();
        } else {
            if (request.version() == null || !request.version().equals(record.getVersion())) {
                throw new ConflictoNegocioException("La conciliación cambió. Actualizá los datos e intentá nuevamente.");
            }
            boolean countChanged = !request.expectedCopyCount().equals(record.getExpectedCopyCount());
            String reason = trimToNull(request.changeReason());
            if (record.getExpectedCopyCount() != null && countChanged && reason == null) {
                throw new NegocioException("Ingresá un motivo para cambiar la cantidad esperada.");
            }
            record.setSourceCustomerCode(sourceCustomerCode.trim());
            record.setExpectedCopyCount(request.expectedCopyCount());
            record.setNote(trimToNull(request.note()));
            record.setExpectedCountChangeReason(countChanged ? reason : record.getExpectedCountChangeReason());
            record.setUpdatedBy(currentActor());
        }
        record = reconciliationRepository.saveAndFlush(record);
        return calculate(record.getSourceCustomerCode(), normalized, record);
    }

    /** Caller holds the source lock and locks source copies before invoking this. */
    @Transactional
    public ManualDiscogsFinalizationSnapshotDTO createFinalizationSnapshot(
            DiscogsManualBatch batch,
            ManualDiscogsSourceReconciliationDTO summary,
            LocalDateTime finalizedAt) {
        snapshotRepository.findByManualBatchId(batch.getId()).ifPresent(existing -> {
            throw new ConflictoNegocioException("El batch Discogs ya tiene una conciliación final registrada.");
        });
        ManualDiscogsFinalizationSnapshot snapshot = snapshotRepository.saveAndFlush(
                ManualDiscogsFinalizationSnapshot.builder()
                        .manualBatch(batch)
                        .sourceCustomerCode(summary.sourceCustomerCode())
                        .normalizedSourceCustomerCode(summary.normalizedSourceCustomerCode())
                        .expectedCopyCount(summary.expectedCopyCount())
                        .provablePhysicalCopyCount(summary.provablePhysicalCopyCount())
                        .availableCopyCount(summary.availableCopyCount())
                        .soldCopyCount(summary.soldCopyCount())
                        .removedCopyCount(summary.removedCopyCount())
                        .distinctReleaseCount(summary.distinctReleaseCount())
                        .duplicateReleaseGroupCount(summary.duplicateReleaseGroupCount())
                        .extraDuplicateCopyCount(summary.extraDuplicateCopyCount())
                        .pendingOperationCount(summary.pendingOperationCount())
                        .completedOperationCount(summary.completedOperationCount())
                        .abandonedOperationCount(summary.abandonedOperationCount())
                        .difference(summary.difference())
                        .reconciliationStatus(summary.reconciliationStatus())
                        .finalizedAt(finalizedAt)
                        .finalizedBy(currentActor())
                        .build());
        return toDto(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ManualDiscogsFinalizationSnapshotDTO> snapshots(String sourceCustomerCode) {
        String normalized = DiscogsManualBatchService.normalizeCustomerCode(sourceCustomerCode);
        return snapshotRepository.findByNormalizedSourceCustomerCodeOrderByFinalizedAtDesc(normalized)
                .stream().map(this::toDto).toList();
    }

    public List<String> warnings(ManualDiscogsSourceReconciliationDTO summary) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        if (summary.expectedCopyCount() == null) {
            warnings.add("No se definió una cantidad esperada.");
        } else if (summary.difference() != null && summary.difference() != 0) {
            warnings.add("La cantidad registrada no coincide con la esperada.");
        }
        if (summary.pendingOperationCount() > 0) {
            warnings.add(summary.pendingOperationCount() == 1
                    ? "Hay 1 importación pendiente."
                    : "Hay " + summary.pendingOperationCount() + " importaciones pendientes.");
        }
        return List.copyOf(warnings);
    }

    private ManualDiscogsSourceReconciliationDTO calculate(
            String displaySource,
            String normalized,
            ManualDiscogsSourceReconciliation record) {
        ManualDiscogsSourceCopyAggregateProjection copies = copyRepository
                .aggregateRetainedByManualSource(normalized);
        long provable = copies == null ? 0 : copies.getProvablePhysicalCopyCount();
        long available = copies == null ? 0 : copies.getAvailableCopyCount();
        long sold = copies == null ? 0 : copies.getSoldCopyCount();
        long removed = copies == null ? 0 : copies.getRemovedCopyCount();
        long distinct = copies == null ? 0 : copies.getDistinctReleaseCount();

        List<Long> duplicateGroups = copyRepository.countDuplicateReleaseGroupsByManualSource(normalized);
        long duplicateGroupCount = duplicateGroups.size();
        long extraDuplicateCopies = duplicateGroups.stream().mapToLong(count -> count - 1).sum();

        Map<ManualDiscogsImportOperationStatus, Long> operations =
                new EnumMap<>(ManualDiscogsImportOperationStatus.class);
        operationRepository.countStatusesByNormalizedSource(normalized)
                .forEach(row -> operations.put(row.getStatus(), row.getOperationCount()));
        long pending = operations.getOrDefault(ManualDiscogsImportOperationStatus.PENDING, 0L);
        long completed = operations.getOrDefault(ManualDiscogsImportOperationStatus.COMPLETED, 0L);
        long abandoned = operations.getOrDefault(ManualDiscogsImportOperationStatus.ABANDONED, 0L);

        Integer expected = record == null ? null : record.getExpectedCopyCount();
        Long difference = expected == null ? null : provable - expected.longValue();
        ManualDiscogsReconciliationStatus status = expected == null
                ? ManualDiscogsReconciliationStatus.EXPECTED_COUNT_UNKNOWN
                : pending > 0
                    ? ManualDiscogsReconciliationStatus.IN_PROGRESS
                    : difference == 0
                        ? ManualDiscogsReconciliationStatus.MATCHED
                        : ManualDiscogsReconciliationStatus.DIFFERENCE;

        if (provable != available + sold + removed) {
            throw new IllegalStateException("El desglose de copias físicas no coincide con el total retenido.");
        }
        return new ManualDiscogsSourceReconciliationDTO(
                displaySource, normalized, expected, provable, available, sold, removed,
                distinct, duplicateGroupCount, extraDuplicateCopies, pending, completed, abandoned,
                difference, status,
                record == null ? null : record.getNote(),
                record == null ? null : record.getExpectedCountChangeReason(),
                record == null ? null : record.getUpdatedBy(),
                record == null ? null : record.getUpdatedAt(),
                record == null ? null : record.getVersion());
    }

    private ManualDiscogsFinalizationSnapshotDTO toDto(ManualDiscogsFinalizationSnapshot snapshot) {
        return new ManualDiscogsFinalizationSnapshotDTO(
                snapshot.getId(), snapshot.getManualBatch().getId(), snapshot.getSourceCustomerCode(),
                snapshot.getNormalizedSourceCustomerCode(), snapshot.getExpectedCopyCount(),
                snapshot.getProvablePhysicalCopyCount(), snapshot.getAvailableCopyCount(),
                snapshot.getSoldCopyCount(), snapshot.getRemovedCopyCount(), snapshot.getDistinctReleaseCount(),
                snapshot.getDuplicateReleaseGroupCount(), snapshot.getExtraDuplicateCopyCount(),
                snapshot.getPendingOperationCount(), snapshot.getCompletedOperationCount(),
                snapshot.getAbandonedOperationCount(), snapshot.getDifference(),
                snapshot.getReconciliationStatus(), snapshot.getFinalizedAt(), snapshot.getFinalizedBy());
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? "system" : authentication.getName();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
