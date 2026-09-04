package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.repository.DiscoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The catalogue receipt boundary for a resolved concrete Discogs release.
 * Network enrichment deliberately happens before this service is called.
 */
@Service
@RequiredArgsConstructor
public class DiscogsCatalogStockService {

    private final DiscoRepository discoRepository;
    private final DiscoQrCopyService qrCopyService;
    private final PreVentaCodeMatcher preVentaCodeMatcher;
    private final EntityManager entityManager;
    private final DiscoEstadoService discoEstadoService;

    @Transactional
    public ReceiptResult receive(ReceiptCommand command) {
        validate(command);
        lockReleaseIdentity(command.discogsReleaseId());

        Disco existing = resolveExisting(command.discogsReleaseId());
        boolean isNew = existing == null;
        Disco disco = isNew ? newDisco(command) : existing;
        if (!isNew) applyMetadata(disco, command.metadata());

        // A legacy canonical URL may be safely upgraded during this controlled
        // lookup. A conflicting pre-existing identity is never overwritten.
        if (disco.getDiscogsReleaseId() != null
                && !command.discogsReleaseId().equals(disco.getDiscogsReleaseId())) {
            throw new ConflictoNegocioException("El producto encontrado tiene otra identidad Discogs; requiere revisión.");
        }
        disco.setDiscogsReleaseId(command.discogsReleaseId());
        disco.setDiscogsUrl(canonicalReleaseUrl(command.discogsReleaseId()));

        int previousAvailable = isNew ? 0 : availableStock(disco);
        int incomingAvailable = command.incomingCopyState() == EstadoCopiaDisco.DISPONIBLE
                ? command.incomingCopies() : 0;
        int resultingAvailable = previousAvailable + incomingAvailable;
        disco.setCantidadCopias(resultingAvailable);
        if (!isNew) disco.setFechaActualizacion(LocalDateTime.now());
        disco = discoRepository.save(disco);
        List<DiscoQrCopy> createdCopies;
        if (command.incomingCopyState() == EstadoCopiaDisco.DISPONIBLE) {
            DiscoQrCopyService.CopySynchronizationResult synchronization =
                    qrCopyService.synchronizeAvailableCopiesWithResult(disco, resultingAvailable);
            createdCopies = copiesCreatedForReceipt(
                    synchronization.addedCopies(), command.incomingCopies());
        } else {
            qrCopyService.synchronizeAvailableCopies(disco, resultingAvailable);
            createdCopies = qrCopyService.addCopies(
                    disco, command.incomingCopies(), EstadoCopiaDisco.VENDIDO);
        }
        discoEstadoService.aplicar(disco);
        disco = discoRepository.save(disco);
        preVentaCodeMatcher.linkPendingPreSales(disco);

        return new ReceiptResult(disco, isNew ? ProductStatus.NEW_PRODUCT : ProductStatus.EXISTING_PRODUCT,
                command.incomingCopies(), resultingAvailable, createdCopies);
    }

    private List<DiscoQrCopy> copiesCreatedForReceipt(List<DiscoQrCopy> addedCopies, int incomingCopies) {
        if (addedCopies.size() < incomingCopies) {
            throw new IllegalStateException("El inventario QR no informó todas las copias recibidas.");
        }
        // A legacy product can have aggregate stock without QR rows. In that
        // case synchronization materializes legacy rows first; the incoming
        // rows are the final entries created by this receipt operation.
        return List.copyOf(addedCopies.subList(addedCopies.size() - incomingCopies, addedCopies.size()));
    }

    private Disco resolveExisting(Long releaseId) {
        List<Disco> identified = discoRepository.findAllByDiscogsReleaseIdForUpdate(releaseId);
        if (identified.size() > 1) throw ambiguous(releaseId);
        if (!identified.isEmpty()) return identified.getFirst();

        List<Disco> legacy = discoRepository.findAllByDiscogsUrlForUpdate(canonicalReleaseUrl(releaseId));
        if (legacy.size() > 1) throw ambiguous(releaseId);
        if (legacy.isEmpty()) return null;
        Disco candidate = legacy.getFirst();
        if (candidate.getDiscogsReleaseId() != null && !releaseId.equals(candidate.getDiscogsReleaseId())) {
            throw new ConflictoNegocioException("La URL Discogs histórica tiene una identidad en conflicto; requiere revisión.");
        }
        return candidate;
    }

    private ConflictoNegocioException ambiguous(Long releaseId) {
        return new ConflictoNegocioException("Hay más de un producto para el release Discogs " + releaseId
                + ". Revisá los duplicados antes de recibir stock.");
    }

    private Disco newDisco(ReceiptCommand command) {
        DiscogsMetadata metadata = command.metadata();
        Disco disco = Disco.builder()
                .codigoInterno(metadata.codigoInterno())
                .codigoQr(UUID.randomUUID().toString())
                .artista(metadata.artista())
                .album(metadata.album())
                .genero(metadata.genero())
                .selloDiscografico(metadata.selloDiscografico())
                .anio(metadata.anio())
                .condicion(metadata.condicion())
                .condicionFisica(metadata.condicionFisica())
                .tipoDisco(metadata.tipoDisco())
                .formato(metadata.formato())
                .costo(metadata.costo())
                .precioVenta(metadata.precioVenta())
                .pricingMode(metadata.pricingMode())
                .estado(EstadoDisco.DISPONIBLE)
                .pais(metadata.pais())
                .estilo(metadata.estilo())
                .tracklist(metadata.tracklist())
                .imagenUrl(metadata.imagenUrl())
                .previewUrl(metadata.previewUrl())
                .procedencia(metadata.procedencia())
                .notas(metadata.notas())
                .build();
        return disco;
    }

    private void applyMetadata(Disco disco, DiscogsMetadata metadata) {
        if (!blank(metadata.artista())) disco.setArtista(metadata.artista());
        if (!blank(metadata.album())) disco.setAlbum(metadata.album());
        if (!blank(metadata.genero())) disco.setGenero(metadata.genero());
        if (!blank(metadata.selloDiscografico())) disco.setSelloDiscografico(metadata.selloDiscografico());
        if (metadata.anio() != null) disco.setAnio(metadata.anio());
        if (!blank(metadata.pais())) disco.setPais(metadata.pais());
        if (!blank(metadata.estilo())) disco.setEstilo(metadata.estilo());
        if (!blank(metadata.tracklist())) disco.setTracklist(metadata.tracklist());
        if (!blank(metadata.imagenUrl())) disco.setImagenUrl(metadata.imagenUrl());
        if (metadata.previewUrl() != null) disco.setPreviewUrl(metadata.previewUrl());
        if (!blank(metadata.codigoInterno())) disco.setCodigoInterno(metadata.codigoInterno());
        if (!blank(metadata.formato())) disco.setFormato(metadata.formato());
        if (metadata.tipoDisco() != null) disco.setTipoDisco(metadata.tipoDisco());
        if (metadata.condicion() != null) disco.setCondicion(metadata.condicion());
        if (!blank(metadata.condicionFisica())) disco.setCondicionFisica(metadata.condicionFisica());
        if (metadata.precioVenta() != null) {
            disco.setPrecioVenta(metadata.precioVenta());
            disco.setPricingMode(metadata.pricingMode() == null ? PricingMode.MANUAL : metadata.pricingMode());
        }
        if (!blank(metadata.procedencia())) disco.setProcedencia(metadata.procedencia());
        if (!blank(metadata.notas())) disco.setNotas(mergeNotes(disco.getNotas(), metadata.notas()));
    }

    private int availableStock(Disco disco) {
        if (disco.getIdDisco() != null && qrCopyService.hasCopyInventory(disco.getIdDisco())) {
            return Math.toIntExact(qrCopyService.countAvailableCopies(disco.getIdDisco()));
        }
        return Math.max(0, disco.getCantidadCopias() == null ? 0 : disco.getCantidadCopias());
    }

    /** PostgreSQL serializes concurrent new-release receipts without requiring an unsafe UNIQUE migration. */
    private void lockReleaseIdentity(Long releaseId) {
        Object dialect = entityManager.getEntityManagerFactory().getProperties().get("hibernate.dialect");
        if (dialect != null && dialect.toString().toLowerCase().contains("postgres")) {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(CAST(:releaseId AS BIGINT))")
                    .setParameter("releaseId", releaseId)
                    .getSingleResult();
        }
    }

    private void validate(ReceiptCommand command) {
        if (command == null || command.discogsReleaseId() == null || command.discogsReleaseId() < 1) {
            throw new IllegalArgumentException("Se requiere un release concreto de Discogs.");
        }
        if (command.incomingCopies() < 1) {
            throw new IllegalArgumentException("La cantidad de copias debe ser mayor a cero.");
        }
        if (command.incomingCopyState() == null) {
            throw new IllegalArgumentException("El estado de la copia entrante es obligatorio.");
        }
        if (command.metadata() == null || blank(command.metadata().artista()) || blank(command.metadata().album())) {
            throw new IllegalArgumentException("No se pudo obtener metadata válida de Discogs para crear el producto.");
        }
    }

    private String canonicalReleaseUrl(Long releaseId) {
        return "https://www.discogs.com/release/" + releaseId;
    }

    private String mergeNotes(String existing, String incoming) {
        if (blank(existing) || existing.contains(incoming)) return blank(existing) ? incoming : existing;
        return existing + "\n" + incoming;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public enum ProductStatus { NEW_PRODUCT, EXISTING_PRODUCT }

    public record ReceiptResult(
            Disco disco,
            ProductStatus productStatus,
            int addedCopies,
            int resultingAvailableCopies,
            List<DiscoQrCopy> createdCopies
    ) {
        public ReceiptResult(Disco disco, ProductStatus productStatus,
                             int addedCopies, int resultingAvailableCopies) {
            this(disco, productStatus, addedCopies, resultingAvailableCopies, List.of());
        }
    }

    public record ReceiptCommand(
            Long discogsReleaseId,
            int incomingCopies,
            DiscogsMetadata metadata,
            EstadoCopiaDisco incomingCopyState
    ) {
        public ReceiptCommand(Long discogsReleaseId, int incomingCopies, DiscogsMetadata metadata) {
            this(discogsReleaseId, incomingCopies, metadata, EstadoCopiaDisco.DISPONIBLE);
        }
    }

    public record DiscogsMetadata(
            String artista, String album, String genero, String selloDiscografico, Integer anio,
            CondicionDisco condicion, String condicionFisica, TipoDisco tipoDisco, String formato,
            BigDecimal costo, BigDecimal precioVenta, PricingMode pricingMode, String pais, String estilo,
            String tracklist, String imagenUrl, String previewUrl, String codigoInterno, String procedencia,
            String notas) {}
}
