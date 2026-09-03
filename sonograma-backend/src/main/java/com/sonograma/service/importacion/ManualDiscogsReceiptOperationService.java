package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.entity.ManualDiscogsImportOperation;
import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ManualDiscogsImportOperationRepository;
import com.sonograma.service.DiscogsCatalogStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gives one manual confirmation a durable identity.  This service deliberately
 * joins the shared catalogue receipt transaction so a completed operation and
 * its stock/QR change commit together.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualDiscogsReceiptOperationService {

    private final ManualDiscogsImportOperationRepository operationRepository;

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
                .build();
        operationRepository.save(operation);
        return operation.getOperationId();
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

        DiscogsCatalogStockService.ReceiptResult receipt = receiptExecutor.receive();
        operation.setStatus(ManualDiscogsImportOperationStatus.COMPLETED);
        operation.setResultingProductId(receipt.disco().getIdDisco());
        operation.setResultType(receipt.productStatus().name());
        operation.setAvailableCopies(receipt.resultingAvailableCopies());
        operationRepository.save(operation);
        log.info("Discogs manual operation completed operationId={} release={} product={} type={} copiesAdded={}",
                operationId, operation.getDiscogsReleaseId(), receipt.disco().getIdDisco(),
                receipt.productStatus(), receipt.addedCopies());
        return ManualDiscogsImportResultDTO.builder()
                .operationId(operationId.toString())
                .productId(receipt.disco().getIdDisco())
                .resultType(receipt.productStatus().name())
                .copiesAdded(receipt.addedCopies())
                .availableCopies(receipt.resultingAvailableCopies())
                .alreadyProcessed(false)
                .build();
    }

    public void validateOperation(DiscoImportPreviewDTO preview) {
        UUID operationId = parseOperationId(preview == null ? null : preview.getOperationId());
        ManualDiscogsImportOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new NegocioException("La operación de importación no es válida."));
        validatePreviewMatchesOperation(preview, operation);
    }

    private ManualDiscogsImportResultDTO result(ManualDiscogsImportOperation operation, boolean alreadyProcessed) {
        return ManualDiscogsImportResultDTO.builder()
                .operationId(operation.getOperationId().toString())
                .productId(operation.getResultingProductId())
                .resultType(alreadyProcessed ? "ALREADY_COMPLETED_OPERATION" : operation.getResultType())
                .copiesAdded(0)
                .availableCopies(operation.getAvailableCopies())
                .alreadyProcessed(alreadyProcessed)
                .build();
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

    @FunctionalInterface
    public interface ReceiptExecutor {
        DiscogsCatalogStockService.ReceiptResult receive();
    }
}
