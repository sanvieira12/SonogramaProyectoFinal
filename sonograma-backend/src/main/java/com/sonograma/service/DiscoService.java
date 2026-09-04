package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsCatalogJobFilterDTO;
import com.sonograma.dto.DiscogsCatalogSourceDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DiscoService {

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
            disco.setPricingMode(com.sonograma.enums.PricingMode.AUTO);
        }
        catalogPricingService.applyPricingToDisco(disco, request);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO obtenerPorId(Long id) {
        return discoRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
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
        discogsManualBatchRepository.findCatalogSources().stream()
                .map(this::withManualLabel)
                .forEach(sources::add);
        sources.sort(java.util.Comparator.comparing(
                DiscogsCatalogSourceDTO::createdAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return sources;
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
        String label = String.format("%s · %d discos · %s", source.customerCode(), source.productos(), statusLabel);
        return new DiscogsCatalogSourceDTO(
                source.key(), source.type(), label, source.productos(), source.customerCode(),
                source.status(), source.batchId(), source.createdAt());
    }

    private boolean isManualSource(String source) {
        return source.trim().toLowerCase(Locale.ROOT).startsWith("manual:");
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
        return discoRepository.findAll().stream()
                .filter(d -> coincide(d, query))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public DiscoResponseDTO actualizarDisco(Long id, DiscoRequestDTO request) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        DiscoMapper.updateFromRequest(disco, request);
        catalogPricingService.applyPricingToDisco(disco, request);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO cambiarEstado(Long id, EstadoDisco nuevoEstado) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        disco.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoDisco.VENDIDO) {
            qrCopyService.marcarDisponiblesVendidas(disco);
            disco.setCantidadCopias(0);
        } else if (nuevoEstado == EstadoDisco.SIN_STOCK) {
            disco.setCantidadCopias(0);
            qrCopyService.synchronizeAvailableCopies(disco, 0);
        }
        return saveWithQr(disco);
    }

    public DiscoResponseDTO actualizarCopias(Long id, Integer cantidad) {
        if (cantidad < 0) {
            throw new NegocioException("La cantidad de copias no puede ser negativa");
        }
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        qrCopyService.synchronizeAvailableCopies(disco, cantidad);
        disco.setCantidadCopias(cantidad);
        discoEstadoService.aplicar(disco);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO cambiarEstadoCopia(Long idDisco, Long idCopia, EstadoCopiaDisco nuevoEstado) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
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
        if (copyHasHistoricalCommerce(copy, idDisco)) {
            throw new ConflictoNegocioException(
                    "No se puede eliminar la copia porque está vinculada a historial de ventas.");
        }

        discoQrCopyRepository.delete(copy);
        discoQrCopyRepository.flush();
        int available = Math.toIntExact(discoQrCopyRepository.countByIdDiscoAndEstado(
                idDisco, EstadoCopiaDisco.DISPONIBLE));
        qrCopyService.synchronizeAvailableCopies(disco, available);
        discoEstadoService.aplicar(disco);
        discoRepository.saveAndFlush(disco);
        return toDTO(disco);
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
                || exists("SELECT COUNT(*) FROM pre_venta WHERE id_disco = :id", id);
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
        if ((disco.getCantidadCopias() == null || disco.getCantidadCopias() > 0)
                && qrCopyService.listDtos(disco).isEmpty()) {
            qrCopyService.synchronize(disco);
        }
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
        Disco saved = discoRepository.save(disco);
        qrCopyService.synchronize(saved);
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
            qrCopyService.synchronizeAvailableCopies(disco, disco.getCantidadCopias());
            available = qrCopyService.countAvailableCopies(disco.getIdDisco());
        }
        return (int) available;
    }

    private boolean contiene(String valor, String query) {
        return valor != null && normalizar(valor).contains(query);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
