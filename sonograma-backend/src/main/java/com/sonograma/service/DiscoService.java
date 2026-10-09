package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoQrCopyDetailDTO;
import com.sonograma.dto.DiscoSaleSearchCopyDTO;
import com.sonograma.dto.DiscoSaleSearchProductRow;
import com.sonograma.dto.DiscoSaleSearchResultDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsCatalogJobFilterDTO;
import com.sonograma.dto.DiscogsCatalogSourceDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.DisposicionCopiaReason;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.PricingMode;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.repository.VentaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DiscoService {

    private static final int DEFAULT_SALE_SEARCH_LIMIT = 20;
    private static final int MAX_SALE_SEARCH_LIMIT = 50;

    private final DiscoRepository discoRepository;
    private final DiscoQrCopyRepository discoQrCopyRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final VentaRepository ventaRepository;
    private final DiscogsImportRowRepository discogsImportRowRepository;
    private final DiscogsManualBatchRepository discogsManualBatchRepository;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final DiscoEstadoService discoEstadoService;
    private final CatalogPricingService catalogPricingService;
    private final PreVentaCodeMatcher preVentaCodeMatcher;
    private final EntityManager entityManager;

    public DiscoResponseDTO crearDisco(DiscoRequestDTO request) {
        Disco disco = DiscoMapper.toEntity(request);
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setCodigoQr(UUID.randomUUID().toString());
        if (disco.getPricingMode() == null) {
            disco.setPricingMode(PricingMode.AUTO);
        }
        catalogPricingService.applyPricingToDisco(disco, request);
        return saveWithQr(disco, request);
    }

    public DiscoResponseDTO obtenerPorId(Long id) {
        return discoRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
    }

    @Transactional(readOnly = true)
    public List<DiscoQrCopyDetailDTO> obtenerCopias(Long id) {
        if (!discoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Disco", id);
        }
        return qrCopyService.listDetailDtos(id);
    }

    public DiscoResponseDTO obtenerPorQR(String codigoQr) {
        DiscoQrCopy qrCopy = qrCopyService.findByCode(codigoQr);
        if (qrCopy != null) {
            return discoRepository.findById(qrCopy.getIdDisco())
                    .map(this::toDTO)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Disco", qrCopy.getIdDisco()));
        }
        return discoRepository.findByCodigoQr(codigoQr)
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco no encontrado con QR: " + codigoQr));
    }

    public List<DiscoResponseDTO> obtenerTodos() {
        return obtenerTodos(null);
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> obtenerTodos(Long discogsImportJobId) {
        return obtenerTodos(discogsImportJobId, null);
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> obtenerTodos(Long discogsImportJobId, String discogsSource) {
        if (discogsImportJobId != null) {
            return discogsImportRowRepository.findDistinctActiveCatalogProductsByJobId(discogsImportJobId).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        if (discogsSource == null || discogsSource.isBlank()) {
            return discoRepository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        if (isManualSource(discogsSource)) {
            if (isManualCustomerSource(discogsSource)) {
                return obtenerPorCustomerCodeManual(parseManualCustomerCode(discogsSource));
            }
            return obtenerPorBatchManual(parseManualBatchId(discogsSource));
        }
        return discogsImportRowRepository.findDistinctActiveCatalogProductsBySource(excelSourceName(discogsSource)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscogsCatalogJobFilterDTO> listarFiltrosImportacionDiscogs() {
        return discogsImportRowRepository.findCatalogJobFilters();
    }

    @Transactional(readOnly = true)
    public List<DiscogsCatalogSourceDTO> listarFuentesImportacionDiscogs() {
        List<DiscogsCatalogSourceDTO> sources = new java.util.ArrayList<>(discogsImportRowRepository.findCatalogSources());
        sources.addAll(listarFuentesManualesAgrupadas());
        sources.sort(java.util.Comparator.comparing(
                DiscogsCatalogSourceDTO::createdAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return sources;
    }

    private List<DiscogsCatalogSourceDTO> listarFuentesManualesAgrupadas() {
        Map<String, List<DiscogsManualBatch>> batchesByCustomer =
                discogsManualBatchRepository.findAllWithCopiesForCatalog().stream()
                        .map(batch -> java.util.Map.entry(normalizedManualCustomerCode(batch), batch))
                        .filter(entry -> entry.getKey() != null)
                        .collect(Collectors.groupingBy(
                                java.util.Map.Entry::getKey,
                                java.util.LinkedHashMap::new,
                                Collectors.mapping(java.util.Map.Entry::getValue, Collectors.toList())));

        return batchesByCustomer.entrySet().stream()
                .map(entry -> {
                    String customerCode = entry.getKey();
                    List<DiscogsManualBatch> batches = entry.getValue();
                    DiscogsManualBatch representative = batches.stream()
                            .filter(batch -> batch.getStatus() == DiscogsManualBatchStatus.OPEN)
                            .findFirst()
                            .orElse(batches.get(0));
                    DiscogsManualBatchStatus status = batches.stream()
                            .anyMatch(batch -> batch.getStatus() == DiscogsManualBatchStatus.OPEN)
                            ? DiscogsManualBatchStatus.OPEN
                            : DiscogsManualBatchStatus.FINALIZED;
                    long copyCount = batches.stream()
                            .mapToLong(batch -> batch.getCopies() == null ? 0 : batch.getCopies().size())
                            .sum();
                    return withManualLabel(new DiscogsCatalogSourceDTO(
                            "manual:customer:" + customerCode,
                            "MANUAL",
                            null,
                            copyCount,
                            customerCode,
                            status,
                            representative.getId(),
                            batches.stream()
                                    .map(DiscogsManualBatch::getCreatedAt)
                                    .max(java.util.Comparator.naturalOrder())
                                    .orElse(representative.getCreatedAt()),
                            representative.getPorcentajeSonograma()));
                })
                .toList();
    }

    /**
     * Historical rows are grouped from the display customer code when it is
     * available, with the normalized column as a fallback. This also makes
     * the projection tolerant of old rows created before that column was
     * populated.
     */
    private String normalizedManualCustomerCode(DiscogsManualBatch batch) {
        String raw = batch.getCustomerCode() != null && !batch.getCustomerCode().isBlank()
                ? batch.getCustomerCode() : batch.getNormalizedCustomerCode();
        try {
            return DiscogsManualBatchService.normalizeCustomerCode(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Omitiendo batch Discogs {} sin código de cliente válido", batch.getId());
            return null;
        }
    }

    private List<DiscoResponseDTO> obtenerPorBatchManual(Long batchId) {
        DiscogsManualBatch batch = discogsManualBatchRepository.findById(batchId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Batch Discogs", batchId));
        List<DiscoQrCopy> copies = discoQrCopyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batchId);
        if (copies.isEmpty()) return List.of();

        List<Long> productIds = copies.stream()
                .map(DiscoQrCopy::getIdDisco)
                .distinct()
                .toList();
        java.util.Map<Long, List<DiscoQrCopy>> copiesByProduct = copies.stream()
                .collect(Collectors.groupingBy(DiscoQrCopy::getIdDisco));
        return discoRepository.findAllById(productIds).stream()
                .map(disco -> toDTO(
                        disco,
                        copiesByProduct.getOrDefault(disco.getIdDisco(), List.of()),
                        batch.getCustomerCode()))
                .collect(Collectors.toList());
    }

    private List<DiscoResponseDTO> obtenerPorCustomerCodeManual(String normalizedCustomerCode) {
        List<DiscoQrCopy> copies = discoQrCopyRepository
                .findByManualCustomerCodeOrderByCopyNumber(normalizedCustomerCode);
        if (copies.isEmpty()) return List.of();

        List<Long> productIds = copies.stream()
                .map(DiscoQrCopy::getIdDisco)
                .distinct()
                .toList();
        Map<Long, List<DiscoQrCopy>> copiesByProduct = copies.stream()
                .collect(Collectors.groupingBy(DiscoQrCopy::getIdDisco));
        return discoRepository.findAllById(productIds).stream()
                .map(disco -> toDTO(
                        disco,
                        copiesByProduct.getOrDefault(disco.getIdDisco(), List.of()),
                        normalizedCustomerCode))
                .collect(Collectors.toList());
    }

    private DiscoResponseDTO toDTO(Disco disco, List<DiscoQrCopy> batchCopies, String customerCode) {
        DiscoResponseDTO dto = toDTO(disco);
        dto.setManualBatchCustomerCode(customerCode);
        if (batchCopies.size() == 1) {
            DiscoQrCopy copy = batchCopies.get(0);
            dto.setManualBatchPrecioVenta(copy.getPrecioVenta());
            dto.setManualBatchCondicionFisica(copy.getCondicionFisica());
        }
        return dto;
    }

    private DiscogsCatalogSourceDTO withManualLabel(DiscogsCatalogSourceDTO source) {
        String statusLabel = source.status() == com.sonograma.enums.DiscogsManualBatchStatus.FINALIZED
                ? "Finalizada" : "En curso";
        String label = String.format("%s · %d copias físicas · %s", source.customerCode(), source.productos(), statusLabel);
        return new DiscogsCatalogSourceDTO(
                source.key(), source.type(), label, source.productos(), source.customerCode(),
                source.status(), source.batchId(), source.createdAt(), source.porcentajeSonograma());
    }

    private boolean isManualSource(String source) {
        return source.trim().toLowerCase(Locale.ROOT).startsWith("manual:");
    }

    private boolean isManualCustomerSource(String source) {
        return source.trim().regionMatches(true, 0, "manual:customer:", 0,
                "manual:customer:".length());
    }

    private String parseManualCustomerCode(String source) {
        String raw = source.trim().substring("manual:customer:".length());
        try {
            return DiscogsManualBatchService.normalizeCustomerCode(raw);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException("La selección de cliente Discogs no es válida.");
        }
    }

    private Long parseManualBatchId(String source) {
        try {
            long id = Long.parseLong(source.trim().substring("manual:".length()));
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new NegocioException("La selección de batch Discogs no es válida.");
        }
    }

    private String excelSourceName(String source) {
        String normalized = source.trim();
        if (normalized.regionMatches(true, 0, "excel:", 0, "excel:".length())) {
            return normalized.substring("excel:".length()).trim();
        }
        return normalized;
    }

    public List<DiscoResponseDTO> obtenerDisponibles() {
        return discoRepository.findAll().stream()
                .filter(disco -> copiasDisponibles(disco) > 0)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<DiscoResponseDTO> obtenerPorEstado(EstadoDisco estado) {
        return discoRepository.findByEstado(estado).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<DiscoResponseDTO> buscar(String q) {
        String query = normalizar(q);
        if (query.isBlank()) {
            return obtenerTodos();
        }
        String normalizedCustomerCode = DiscogsManualBatchService.normalizeCustomerCode(q);
        if (discogsManualBatchRepository.existsByNormalizedCustomerCode(normalizedCustomerCode)) {
            return discoRepository.findAllByManualCustomerCode(normalizedCustomerCode).stream()
                    .map(disco -> toDTO(disco, List.of(), normalizedCustomerCode))
                    .collect(Collectors.toList());
        }
        return discoRepository.findAll().stream()
                .filter(d -> coincide(d, query))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoSaleSearchResultDTO> buscarParaVenta(String rawQuery, Integer requestedLimit) {
        String query = normalizar(rawQuery);
        if (query.length() < 2) return List.of();

        int limit = requestedLimit == null
                ? DEFAULT_SALE_SEARCH_LIMIT
                : Math.max(1, Math.min(requestedLimit, MAX_SALE_SEARCH_LIMIT));
        String sourceCode = DiscogsManualBatchService.normalizeCustomerCode(rawQuery);
        List<DiscoSaleSearchProductRow> products = discoRepository.searchForSale(
                query,
                "%" + query + "%",
                query + "%",
                sourceCode,
                PageRequest.of(0, limit));
        if (products.isEmpty()) return List.of();

        List<Long> productIds = products.stream().map(DiscoSaleSearchProductRow::idDisco).toList();
        Map<Long, List<DiscoQrCopy>> copiesByProduct = discoQrCopyRepository
                .findAvailableSaleChoicesByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        DiscoQrCopy::getIdDisco,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        return products.stream()
                .map(product -> toSaleSearchResult(
                        product,
                        copiesByProduct.getOrDefault(product.idDisco(), List.of())))
                .toList();
    }

    private DiscoSaleSearchResultDTO toSaleSearchResult(
            DiscoSaleSearchProductRow product,
            List<DiscoQrCopy> copies) {
        List<DiscoSaleSearchCopyDTO> copyChoices = copies.stream()
                .map(copy -> {
                    DiscogsManualBatch batch = copy.getManualDiscogsBatch();
                    return new DiscoSaleSearchCopyDTO(
                            copy.getId(),
                            copy.getCopyNumber(),
                            copy.getCodigoQr(),
                            copy.getPrecioVenta(),
                            copy.getCondicionFisica(),
                            batch == null ? null : batch.getCustomerCode(),
                            batch == null ? null : batch.getNormalizedCustomerCode(),
                            batch == null ? null : batch.getId(),
                            copy.getEstado().name());
                })
                .toList();
        return new DiscoSaleSearchResultDTO(
                product.idDisco(),
                product.artista(),
                product.album(),
                product.anio(),
                product.codigoInterno(),
                product.imagenUrl(),
                product.condicion() == null ? null : product.condicion().name(),
                product.condicionFisica(),
                product.tipoDisco() == null ? null : product.tipoDisco().name(),
                product.formato(),
                product.selloDiscografico(),
                product.precioVenta(),
                product.estado().name(),
                product.condicion() == com.sonograma.enums.CondicionDisco.USADO
                        || copies.stream().anyMatch(copy -> copy.getManualDiscogsBatch() != null),
                copyChoices.size(),
                copyChoices);
    }

    public DiscoResponseDTO actualizarDisco(Long id, DiscoRequestDTO request) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        DiscoMapper.updateFromRequest(disco, request);
        rejectUnsafeAggregateChange(disco, request.getCantidadCopias());
        if (request.getCantidadCopias() == null && qrCopyService.hasCopyInventory(id)) {
            disco.setCantidadCopias(Math.toIntExact(qrCopyService.countAvailableCopies(id)));
        }
        catalogPricingService.applyPricingToDisco(disco, request);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO cambiarEstado(Long id, EstadoDisco nuevoEstado) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        long availableCopies = qrCopyService.countAvailableCopies(id);
        if (availableCopies > 0
                && (nuevoEstado == EstadoDisco.VENDIDO || nuevoEstado == EstadoDisco.SIN_STOCK)) {
            throw new ConflictoNegocioException(
                    "El estado del producto se deriva de sus copias físicas; use el flujo de venta o retiro correspondiente.");
        }
        disco.setEstado(nuevoEstado);
        discoEstadoService.aplicar(disco);
        return toDTO(discoRepository.save(disco));
    }

    public DiscoResponseDTO actualizarCopias(Long id, Integer cantidad) {
        if (cantidad < 0) {
            throw new NegocioException("La cantidad de copias no puede ser negativa");
        }
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        rejectUnsafeAggregateChange(disco, cantidad);
        qrCopyService.synchronizeAvailableCopies(disco, cantidad);
        discoEstadoService.aplicar(disco);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO cambiarEstadoCopia(Long idDisco, Long idCopia, EstadoCopiaDisco nuevoEstado) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
        DiscoQrCopy current = discoQrCopyRepository.findByIdForUpdate(idCopia)
                .filter(copy -> idDisco.equals(copy.getIdDisco()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Copia", idCopia));
        if (current.getEstado() == EstadoCopiaDisco.VENDIDO
                && nuevoEstado == EstadoCopiaDisco.DISPONIBLE
                && copyHasHistoricalCommerce(current, idDisco)) {
            throw new ConflictoNegocioException(
                    "Una copia vinculada a historial de ventas solo puede restaurarse mediante la cancelación exacta.");
        }
        qrCopyService.changeCopyStatus(disco, idCopia, nuevoEstado);
        disco.setCantidadCopias((int) qrCopyService.countAvailableCopies(idDisco));
        discoEstadoService.aplicar(disco);
        return saveWithQr(disco);
    }

    /** Removes exactly one physical copy while preserving the parent catalogue record. */
    public DiscoResponseDTO eliminarCopia(Long idDisco, Long idCopia) {
        Disco disco = discoRepository.findByIdForUpdate(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
        DiscoQrCopy copy = discoQrCopyRepository.findByIdForUpdate(idCopia)
                .orElseThrow(() -> new RecursoNoEncontradoException("Copia", idCopia));
        if (!idDisco.equals(copy.getIdDisco())) {
            throw new RecursoNoEncontradoException("Copia", idCopia);
        }
        if (copy.getManualDiscogsBatch() != null) {
            throw new ConflictoNegocioException(
                    "La copia manual USED conserva su historial; retírela con un motivo explícito.");
        }
        assertCopyHasNoDestructiveCommercialReferences(copy, idDisco);

        discoQrCopyRepository.delete(copy);
        discoQrCopyRepository.flush();
        int available = Math.toIntExact(discoQrCopyRepository.countByIdDiscoAndEstado(
                idDisco, EstadoCopiaDisco.DISPONIBLE));
        qrCopyService.synchronizeAvailableCopies(disco, available);
        discoEstadoService.aplicar(disco);
        discoRepository.saveAndFlush(disco);
        return toDTO(disco);
    }

    public DiscoResponseDTO retirarCopia(
            Long idDisco,
            Long idCopia,
            DisposicionCopiaReason reason,
            String note,
            String actor) {
        Disco disco = discoRepository.findByIdForUpdate(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
        DiscoQrCopy copy = discoQrCopyRepository.findByIdForUpdate(idCopia)
                .orElseThrow(() -> new RecursoNoEncontradoException("Copia", idCopia));
        if (!idDisco.equals(copy.getIdDisco())) {
            throw new RecursoNoEncontradoException("Copia", idCopia);
        }
        assertCopyHasNoDestructiveCommercialReferences(copy, idDisco);
        qrCopyService.removePhysicalCopy(disco, idCopia, reason, note, actor);
        discoEstadoService.aplicar(disco);
        discoRepository.saveAndFlush(disco);
        return toDTO(disco);
    }

    private void assertCopyHasNoDestructiveCommercialReferences(DiscoQrCopy copy, Long idDisco) {
        if (copyHasHistoricalCommerce(copy, idDisco)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar ni retirar la copia porque está vinculada a historial de ventas.");
        }

        // Reserva and PreVenta currently identify only the product, not a physical copy.
        // Until those legacy models carry exact-copy identity, protect commercial integrity
        // conservatively by blocking every destructive copy operation for that product.
        if (exists("SELECT COUNT(*) FROM reserva WHERE id_disco = :id AND estado = 'ACTIVA'", idDisco)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar ni retirar una copia mientras el disco tenga una reserva activa.");
        }
        if (exists("SELECT COUNT(*) FROM pre_venta WHERE id_disco = :id AND estado <> 'PAGADA'", idDisco)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar ni retirar una copia mientras el disco tenga una preventa pendiente.");
        }
    }

    private boolean copyHasHistoricalCommerce(DiscoQrCopy copy, Long idDisco) {
        if (copy.getId() == null) return true;
        if (detalleVentaRepository.findAllWithCopyIds().stream()
                .anyMatch(detail -> containsCopyId(detail.getCopyIdsSnapshot(), copy.getId()))) {
            return true;
        }
        if (copy.getEstado() != EstadoCopiaDisco.VENDIDO) return false;

        // Older sales may reference only the parent product, not a copy snapshot.
        // Block sold-copy deletion in that ambiguous case rather than guessing.
        return ventaRepository.countByDiscoIdDisco(idDisco) > 0
                || detalleVentaRepository.countByDiscoIdDisco(idDisco) > 0;
    }

    private boolean containsCopyId(String snapshot, Long copyId) {
        if (snapshot == null || snapshot.isBlank()) return false;
        for (String token : snapshot.split(",")) {
            if (String.valueOf(copyId).equals(token.trim())) return true;
        }
        return false;
    }

    public void eliminarDisco(Long id, String deletedBy) {
        Disco disco = discoRepository.findByIdIncludingCatalogDeleted(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getCatalogDeletedAt() != null) {
            throw new RecursoNoEncontradoException("Disco", id);
        }
        if (exists("SELECT COUNT(*) FROM reserva WHERE id_disco = :id AND estado = 'ACTIVA'", id)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar el disco mientras tenga una reserva activa");
        }
        if (exists("SELECT COUNT(*) FROM pre_venta WHERE id_disco = :id AND estado <> 'PAGADA'", id)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar el disco mientras tenga una preventa pendiente");
        }

        try {
            detachImportReferences(id);
            if (hasHistoricalReferences(id)) {
                disco.setCatalogDeletedAt(LocalDateTime.now());
                disco.setCatalogDeletedBy(normalizeDeletedBy(deletedBy));
                discoRepository.saveAndFlush(disco);
                log.info("Disco {} excluido permanentemente del catálogo conservando historial", id);
                return;
            }

            execute("DELETE FROM catalog_audio_preview WHERE id_disco = :id", id);
            execute("DELETE FROM disco_qr_copy WHERE id_disco = :id", id);
            discoRepository.delete(disco);
            discoRepository.flush();
            log.info("Disco {} eliminado permanentemente del catálogo", id);
        } catch (DataIntegrityViolationException ex) {
            log.warn("La eliminación permanente del disco {} fue bloqueada por integridad referencial", id);
            throw new ConflictoNegocioException(
                    "No se pudo eliminar el disco porque todavía está vinculado a información del negocio");
        }
    }

    private boolean hasHistoricalReferences(Long id) {
        return exists("SELECT COUNT(*) FROM detalle_venta WHERE id_disco = :id", id)
                || exists("SELECT COUNT(*) FROM venta WHERE id_disco = :id", id)
                || exists("SELECT COUNT(*) FROM movimiento_stock WHERE id_disco = :id", id)
                || exists("SELECT COUNT(*) FROM reserva WHERE id_disco = :id", id)
                || exists("SELECT COUNT(*) FROM pre_venta WHERE id_disco = :id", id)
                || exists("SELECT COUNT(*) FROM disco_qr_copy WHERE id_disco = :id AND id_discogs_manual_batch IS NOT NULL", id);
    }

    private void detachImportReferences(Long id) {
        execute("UPDATE pedido_item SET id_disco = NULL WHERE id_disco = :id", id);
        execute("UPDATE shipping_order_item SET id_disco = NULL WHERE id_disco = :id", id);
        execute("UPDATE discogs_import_row SET imported_catalog_product_id = NULL WHERE imported_catalog_product_id = :id", id);
    }

    private boolean exists(String sql, Long id) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", id);
        Number result = (Number) query.getSingleResult();
        return result != null && result.longValue() > 0;
    }

    private void execute(String sql, Long id) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", id);
        query.executeUpdate();
    }

    private String normalizeDeletedBy(String deletedBy) {
        if (deletedBy == null || deletedBy.isBlank()) return null;
        String normalized = deletedBy.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private boolean coincide(Disco disco, String query) {
        return contiene(disco.getAlbum(), query)
                || contiene(disco.getArtista(), query)
                || contiene(disco.getGenero(), query)
                || contiene(disco.getSelloDiscografico(), query)
                || contiene(disco.getDescripcion(), query)
                || contiene(disco.getCodigoInterno(), query)
                || contiene(disco.getEstado() != null ? disco.getEstado().name() : null, query)
                || contiene(disco.getCondicion() != null ? disco.getCondicion().name() : null, query)
                || contiene(disco.getCondicionFisica(), query)
                || contiene(disco.getTipoDisco() != null ? disco.getTipoDisco().name() : null, query)
                || contiene(disco.getAnio() != null ? String.valueOf(disco.getAnio()) : null, query);
    }

    private DiscoResponseDTO toDTO(Disco disco) {
        DiscoResponseDTO dto = DiscoMapper.toDTO(disco);
        catalogPricingService.enrichDiscoResponse(disco, dto);
        dto.setAudioPreviews(audioPreviewService.listarPorDisco(disco.getIdDisco()));
        dto.setQrCopies(qrCopyService.listDtos(disco));
        dto.setCantidadCopias((int) qrCopyService.countAvailableCopies(disco.getIdDisco()));
        dto.setTotalCopias(qrCopyService.totalCopies(disco.getIdDisco()));
        dto.setCopiasVendidas(qrCopyService.soldCopies(disco.getIdDisco()));
        return dto;
    }

    private DiscoResponseDTO saveWithQr(Disco disco) {
        return saveWithQr(disco, null);
    }

    private DiscoResponseDTO saveWithQr(Disco disco, DiscoRequestDTO creationRequest) {
        Disco saved = discoRepository.save(disco);
        if (!qrCopyService.hasManualReceiptHistory(saved.getIdDisco())) {
            DiscoQrCopyService.CopySynchronizationResult synchronization =
                    qrCopyService.synchronizeAvailableCopiesWithResult(
                            saved, saved.getCantidadCopias() == null ? 0 : saved.getCantidadCopias());
            if (creationRequest != null) {
                BigDecimal explicitPrice = saved.getPricingMode() == PricingMode.MANUAL
                        ? creationRequest.getPrecioVenta()
                        : null;
                String explicitCondition = creationRequest.getCondicionFisica() == null
                        ? null
                        : saved.getCondicionFisica();
                qrCopyService.initializeCreatedUsedCopyCommercialData(
                        saved, synchronization.addedCopies(), explicitPrice, explicitCondition);
            }
        }
        discoEstadoService.aplicar(saved);
        saved = discoRepository.save(saved);
        preVentaCodeMatcher.linkPendingPreSales(saved);
        return toDTO(saved);
    }

    private int copiasDisponibles(Disco disco) {
        if (disco.getIdDisco() == null) {
            return disco.getCantidadCopias() != null ? Math.max(0, disco.getCantidadCopias()) : 0;
        }
        long available = qrCopyService.countAvailableCopies(disco.getIdDisco());
        if (available == 0 && disco.getCantidadCopias() != null && disco.getCantidadCopias() > 0) {
            log.warn("Disco {} tiene cantidad agregada {} pero ninguna copia física disponible; la lectura no reparará el inventario",
                    disco.getIdDisco(), disco.getCantidadCopias());
        }
        return (int) available;
    }

    private void rejectUnsafeAggregateChange(Disco disco, Integer requested) {
        if (requested == null) return;
        int available = Math.toIntExact(qrCopyService.countAvailableCopies(disco.getIdDisco()));
        boolean usedInventory = disco.getCondicion() == com.sonograma.enums.CondicionDisco.USADO;
        boolean manualInventory = qrCopyService.hasManualReceiptHistory(disco.getIdDisco());
        if (requested < available && (usedInventory || manualInventory)) {
            throw new ConflictoNegocioException(
                    "El stock USED requiere seleccionar la copia física exacta que se retirará.");
        }
        if (requested > available && manualInventory) {
            throw new ConflictoNegocioException(
                    "Otra copia física manual USED debe recibirse mediante el flujo de recepción exacta.");
        }
    }

    private boolean contiene(String valor, String query) {
        return valor != null && normalizar(valor).contains(query);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
