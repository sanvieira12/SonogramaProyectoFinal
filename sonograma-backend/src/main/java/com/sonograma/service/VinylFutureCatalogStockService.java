package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class VinylFutureCatalogStockService {

    private final DiscoRepository discoRepository;
    private final DiscoQrCopyService qrCopyService;
    private final VinylFutureIdentityNormalizer identityNormalizer;

    @Transactional(readOnly = true)
    public Resolution preview(String supplierCode) {
        String identity = identityNormalizer.normalize(supplierCode);
        validateIdentityLength(identity);
        Disco existing = resolveExisting(identity, false);
        return new Resolution(existing, existing == null ? ProductStatus.NEW : ProductStatus.EXISTING, 0, 0);
    }

    @Transactional
    public Resolution addStock(
            String supplierCode,
            int incomingQuantity,
            Supplier<Disco> newProductFactory,
            Consumer<Disco> existingProductEnricher) {
        if (incomingQuantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser un número entero mayor que cero.");
        }
        String identity = identityNormalizer.normalize(supplierCode);
        validateIdentityLength(identity);
        Disco existing = resolveExisting(identity, true);
        boolean isNew = existing == null;
        Disco disco = isNew ? newProductFactory.get() : existing;
        if (disco == null) {
            throw new IllegalArgumentException("No se pudo preparar el producto Vinyl Future.");
        }

        if (identity != null) {
            disco.setVinylFutureSupplierCodeNormalized(identity);
            if (blank(disco.getCodigoInterno())) disco.setCodigoInterno(identity);
        }
        disco.setProcedencia(ImportMetadataNormalizer.SOURCE_FUTURE);

        int previousStock = isNew ? 0 : availableStock(disco);
        if (!isNew && existingProductEnricher != null) existingProductEnricher.accept(disco);
        disco.setCantidadCopias(previousStock + incomingQuantity);
        if (!isNew) {
            // Receiving physical Vinyl Future copies is a catalogue update. Keep
            // fechaIngreso intact and timestamp only the stock mutation itself.
            disco.setFechaActualizacion(LocalDateTime.now());
        }
        disco = discoRepository.save(disco);
        qrCopyService.synchronizeAvailableCopies(disco, previousStock + incomingQuantity);
        disco = discoRepository.save(disco);
        return new Resolution(
            disco,
            isNew ? ProductStatus.NEW : ProductStatus.EXISTING,
            incomingQuantity,
            previousStock + incomingQuantity
        );
    }

    private Disco resolveExisting(String identity, boolean lock) {
        if (identity == null) return null;
        var direct = lock
            ? discoRepository.findVinylFutureByIdentityForUpdate(identity)
            : discoRepository.findByVinylFutureSupplierCodeNormalized(identity);
        if (direct.isPresent()) return direct.get();

        List<Disco> legacy = discoRepository.findAllActiveWithCatalogCode()
            .stream()
            .filter(disco -> ImportMetadataNormalizer.isFutureSource(disco.getProcedencia()))
            .filter(disco -> identity.equals(identityNormalizer.normalize(disco.getCodigoInterno())))
            .toList();
        if (legacy.size() > 1) {
            throw new ConflictoNegocioException(
                "Hay más de un producto Vinyl Future con el código " + identity
                    + ". Revisá los duplicados antes de continuar."
            );
        }
        return legacy.isEmpty() ? null : legacy.getFirst();
    }

    private int availableStock(Disco disco) {
        if (disco.getIdDisco() != null && qrCopyService.hasCopyInventory(disco.getIdDisco())) {
            return Math.toIntExact(qrCopyService.countAvailableCopies(disco.getIdDisco()));
        }
        return Math.max(0, disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias());
    }

    private void validateIdentityLength(String identity) {
        if (identity != null && identity.length() > 180) {
            throw new IllegalArgumentException("El código de catálogo de Vinyl Future es demasiado largo.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public enum ProductStatus { NEW, EXISTING }

    public record Resolution(
        Disco disco,
        ProductStatus status,
        int addedCopies,
        int resultingStock
    ) {}
}
