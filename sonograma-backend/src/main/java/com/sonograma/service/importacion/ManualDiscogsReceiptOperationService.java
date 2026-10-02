package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.ManualDiscogsDuplicateCopyDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.dto.ManualDiscogsOperationContextDTO;
import com.sonograma.dto.ManualDiscogsOperationDTO;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.entity.ManualDiscogsImportOperation;
import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.ManualDiscogsDuplicateException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.ManualDiscogsImportOperationRepository;
import com.sonograma.service.DiscogsCatalogStockService;
import com.sonograma.service.DiscogsManualBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Durable audit boundary for one manually confirmed Discogs receipt. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualDiscogsReceiptOperationService {

    private final ManualDiscogsImportOperationRepository operationRepository;
    private final DiscoQrCopyRepository copyRepository;
    private final DiscogsManualBatchService batchService;
    private final ManualDiscogsReceiptLockService receiptLockService;

    @Transactional
    public UUID createPending(Long releaseId, int requestedCopies) {
        if (releaseId == null || releaseId < 1 || requestedCopies < 1) {
            throw new NegocioException("No se pudo preparar la importación de Discogs.");
        }
        ManualDiscogsImportOperation operation = ManualDiscogsImportOperation.builder()
                .operationId(UUID.randomUUID())
                .discogsReleaseId(releaseId)
                .requestedCopies(requestedCopies)
                .status(ManualDiscogsImportOperationStatus.PENDING)
                .duplicateOverride(false)
                .build();
        operationRepository.save(operation);
        return operation.getOperationId();
    }

    /** Persists context before confirmation so a duplicate conflict remains understandable. */
    @Transactional
    public ManualDiscogsOperationDTO updateContext(UUID operationId, ManualDiscogsOperationContextDTO context) {
        ManualDiscogsImportOperation operation = operationRepository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operación Discogs", 0L));
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.ABANDONED) {
            throw new ConflictoNegocioException("La operación Discogs fue descartada y no puede modificarse.");
        }
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.PENDING) {
            applyContext(operation, context);
            operationRepository.save(operation);
        }
        return toDto(operation);
    }

    @Transactional
    public void capturePendingContext(DiscoImportPreviewDTO preview) {
        UUID operationId = parseOperationId(preview == null ? null : preview.getOperationId());
        ManualDiscogsImportOperation operation = operationRepository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new NegocioException("La confirmación de importación no es válida. Volvé a buscar el release."));
        validatePreviewMatchesOperation(preview, operation);
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.ABANDONED) {
            throw new ConflictoNegocioException("La operación Discogs fue descartada y no puede completarse.");
        }
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.PENDING) {
            applyContext(operation, new ManualDiscogsOperationContextDTO(
                    preview.getCustomerCode(), effectivePrice(preview), preview.getPhysicalCondition()));
            operationRepository.save(operation);
        }
    }

    @Transactional
    public ManualDiscogsImportResultDTO confirm(
            DiscoImportPreviewDTO preview,
            ReceiptExecutor receiptExecutor
    ) {
        UUID operationId = parseOperationId(preview == null ? null : preview.getOperationId());
        ManualDiscogsImportOperation operation = operationRepository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new NegocioException("La confirmación de importación no es válida. Volvé a buscar el release."));

        validatePreviewMatchesOperation(preview, operation);
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.COMPLETED) {
            log.info("Discogs manual operation replay operationId={} release={} product={}",
                    operationId, operation.getDiscogsReleaseId(), operation.getResultingProductId());
            return result(operation, true);
        }
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.ABANDONED) {
            throw new ConflictoNegocioException("La operación Discogs fue descartada y no puede completarse.");
        }

        applyContext(operation, new ManualDiscogsOperationContextDTO(
                preview.getCustomerCode(), effectivePrice(preview), preview.getPhysicalCondition()));
        boolean duplicateOverride = Boolean.TRUE.equals(preview.getDuplicateOverride());
        String overrideReason = trimToNull(preview.getDuplicateOverrideReason());
        if (duplicateOverride && overrideReason == null) {
            throw new NegocioException("Ingresá un motivo para confirmar otra copia física del mismo release.");
        }

        String normalizedSource = operation.getNormalizedSourceCustomerCode();
        receiptLockService.acquire(normalizedSource);
        DiscogsManualBatch batch = batchService.findOrCreateOpenBatch(operation.getSourceCustomerCode());
        List<DiscoQrCopy> duplicates = copyRepository.findRetainedByManualSourceAndRelease(
                normalizedSource, operation.getDiscogsReleaseId());
        if (!duplicates.isEmpty() && !duplicateOverride) {
            throw duplicateConflict(operation, duplicates);
        }
        if (duplicates.isEmpty() && duplicateOverride) {
            throw new NegocioException("No se detectó una recepción previa que requiera anulación de duplicado.");
        }

        DiscogsCatalogStockService.ReceiptResult receipt = receiptExecutor.receive(batch);
        if (receipt.createdCopies().size() != operation.getRequestedCopies()) {
            throw new IllegalStateException("La recepción no informó todas las copias físicas creadas.");
        }
        if (receipt.createdCopies().stream().anyMatch(copy -> copy.getManualDiscogsBatch() == null
                || !batch.getId().equals(copy.getManualDiscogsBatch().getId()))) {
            throw new IllegalStateException("La copia recibida no quedó vinculada al batch de la operación.");
        }

        operation.setManualBatch(batch);
        operation.getResultingCopies().clear();
        operation.getResultingCopies().addAll(receipt.createdCopies());
        operation.setStatus(ManualDiscogsImportOperationStatus.COMPLETED);
        operation.setResultingProductId(receipt.disco().getIdDisco());
        operation.setResultType(receipt.productStatus().name());
        operation.setAvailableCopies(receipt.resultingAvailableCopies());
        operation.setDuplicateOverride(duplicateOverride);
        operation.setDuplicateOverrideReason(duplicateOverride ? overrideReason : null);
        operation.setCompletedAt(LocalDateTime.now());
        operationRepository.save(operation);
        log.info("Discogs manual operation completed operationId={} release={} source={} batch={} product={} copies={}",
                operationId, operation.getDiscogsReleaseId(), normalizedSource, batch.getId(),
                receipt.disco().getIdDisco(), copyIds(operation));
        return result(operation, false);
    }

    @Transactional(readOnly = true)
    public List<ManualDiscogsOperationDTO> listPending(String sourceCustomerCode) {
        String normalized = DiscogsManualBatchService.normalizeCustomerCode(sourceCustomerCode);
        return operationRepository
                .findTop50ByNormalizedSourceCustomerCodeAndStatusOrderByCreatedAtDesc(
                        normalized, ManualDiscogsImportOperationStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ManualDiscogsOperationDTO abandon(UUID operationId) {
        ManualDiscogsImportOperation operation = operationRepository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operación Discogs", 0L));
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.COMPLETED) {
            throw new ConflictoNegocioException("Una operación Discogs completada no puede descartarse.");
        }
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.PENDING) {
            operation.setStatus(ManualDiscogsImportOperationStatus.ABANDONED);
            operation.setAbandonedAt(LocalDateTime.now());
            operationRepository.save(operation);
        }
        return toDto(operation);
    }

    @Transactional(readOnly = true)
    public ManualDiscogsOperationDTO get(UUID operationId) {
        return operationRepository.findWithLineageByOperationId(operationId)
                .map(this::toDto)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operación Discogs", 0L));
    }

    @Transactional(readOnly = true)
    public ManualDiscogsOperationDTO findCreatingOperation(Long copyId) {
        return operationRepository.findCreatingOperationByCopyId(copyId)
                .map(this::toDto)
                .orElseThrow(() -> new RecursoNoEncontradoException("Operación para copia", copyId));
    }

    public void validateOperation(DiscoImportPreviewDTO preview) {
        UUID operationId = parseOperationId(preview == null ? null : preview.getOperationId());
        ManualDiscogsImportOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new NegocioException("La operación de importación no es válida."));
        validatePreviewMatchesOperation(preview, operation);
        if (operation.getStatus() == ManualDiscogsImportOperationStatus.ABANDONED) {
            throw new ConflictoNegocioException("La operación Discogs fue descartada.");
        }
    }

    private ManualDiscogsDuplicateException duplicateConflict(
            ManualDiscogsImportOperation operation, List<DiscoQrCopy> copies) {
        List<ManualDiscogsDuplicateCopyDTO> details = copies.stream()
                .map(copy -> new ManualDiscogsDuplicateCopyDTO(
                        copy.getId(), copy.getCopyNumber(), copy.getEstado().name(),
                        copy.getCondicionFisica(), copy.getPrecioVenta()))
                .toList();
        String source = operation.getSourceCustomerCode();
        return new ManualDiscogsDuplicateException(
                "Este release ya fue ingresado para " + source + ". Confirmá explícitamente si recibiste otra copia física.",
                source, operation.getDiscogsReleaseId(), details);
    }

    private ManualDiscogsImportResultDTO result(ManualDiscogsImportOperation operation, boolean replay) {
        return ManualDiscogsImportResultDTO.builder()
                .operationId(operation.getOperationId().toString())
                .productId(operation.getResultingProductId())
                .resultType(replay ? "ALREADY_COMPLETED_OPERATION" : operation.getResultType())
                .copiesAdded(replay ? 0 : operation.getResultingCopies().size())
                .availableCopies(operation.getAvailableCopies())
                .alreadyProcessed(replay)
                .batchId(operation.getManualBatch() == null ? null : operation.getManualBatch().getId())
                .copyIds(copyIds(operation))
                .sourceCustomerCode(operation.getSourceCustomerCode())
                .build();
    }

    private ManualDiscogsOperationDTO toDto(ManualDiscogsImportOperation operation) {
        return new ManualDiscogsOperationDTO(
                operation.getOperationId(), operation.getDiscogsReleaseId(), operation.getSourceCustomerCode(),
                operation.getNormalizedSourceCustomerCode(), operation.getStatus().name(),
                operation.getRequestedCopies(), operation.getSubmittedPrice(), operation.getSubmittedCondition(),
                operation.getManualBatch() == null ? null : operation.getManualBatch().getId(),
                operation.getResultingProductId(), copyIds(operation), operation.getDuplicateOverride(),
                operation.getDuplicateOverrideReason(), operation.getCreatedAt(), operation.getUpdatedAt(),
                operation.getCompletedAt(), operation.getAbandonedAt());
    }

    private List<Long> copyIds(ManualDiscogsImportOperation operation) {
        return operation.getResultingCopies() == null ? List.of()
                : operation.getResultingCopies().stream().map(DiscoQrCopy::getId).toList();
    }

    private void applyContext(ManualDiscogsImportOperation operation, ManualDiscogsOperationContextDTO context) {
        if (context == null || context.sourceCustomerCode() == null || context.sourceCustomerCode().isBlank()) {
            throw new NegocioException("Ingresá un código de cliente para la operación Discogs.");
        }
        String source = context.sourceCustomerCode().trim();
        operation.setSourceCustomerCode(source);
        operation.setNormalizedSourceCustomerCode(DiscogsManualBatchService.normalizeCustomerCode(source));
        operation.setSubmittedPrice(context.submittedPrice());
        operation.setSubmittedCondition(trimToNull(context.submittedCondition()));
    }

    private BigDecimal effectivePrice(DiscoImportPreviewDTO preview) {
        return preview.getCopySalePrice() != null ? preview.getCopySalePrice() : preview.getPrecioVenta();
    }

    private UUID parseOperationId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new NegocioException("La confirmación de importación no es válida. Volvé a buscar el release.");
        }
    }

    private void validatePreviewMatchesOperation(DiscoImportPreviewDTO preview, ManualDiscogsImportOperation operation) {
        if (preview == null
                || preview.getDiscogsReleaseId() == null
                || !operation.getDiscogsReleaseId().equals(preview.getDiscogsReleaseId())
                || preview.getCantidadCopias() == null
                || !operation.getRequestedCopies().equals(preview.getCantidadCopias())) {
            throw new NegocioException("La confirmación no coincide con el release consultado. Volvé a buscarlo.");
        }
        if (preview.getErrores() != null && !preview.getErrores().isEmpty()) {
            throw new NegocioException("No se puede guardar una importación con metadata incompleta de Discogs.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    public interface ReceiptExecutor {
        DiscogsCatalogStockService.ReceiptResult receive(DiscogsManualBatch batch);
    }
}
