package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsCatalogJobFilterDTO;
import com.sonograma.dto.DiscogsCatalogSourceDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
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

    private static final List<DiscogsCatalogSource> DISCOGS_CATALOG_SOURCES = List.of(
            new DiscogsCatalogSource("pin", "Discos PIN", List.of(22L, 23L)),
            new DiscogsCatalogSource("frank", "Discos FRANK", List.of(20L, 21L)),
            new DiscogsCatalogSource("fede-pintos", "Fede Pintos", List.of(16L)),
            new DiscogsCatalogSource("catalogo-sc", "Catálogo SC", List.of(19L)),
            new DiscogsCatalogSource("lvs", "LVS", List.of(14L)),
            new DiscogsCatalogSource("mati-muten", "Mati Muten", List.of(15L))
    );

    private final DiscoRepository discoRepository;
    private final DiscogsImportRowRepository discogsImportRowRepository;
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
        List<Disco> discos = discogsImportJobId == null
                ? (discogsSource == null || discogsSource.isBlank()
                    ? discoRepository.findAll()
                    : discogsImportRowRepository.findDistinctActiveCatalogProductsByJobIds(source(discogsSource).jobIds()))
                : discogsImportRowRepository.findDistinctActiveCatalogProductsByJobId(discogsImportJobId);
        return discos.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscogsCatalogJobFilterDTO> listarFiltrosImportacionDiscogs() {
        return discogsImportRowRepository.findCatalogJobFilters();
    }

    @Transactional(readOnly = true)
    public List<DiscogsCatalogSourceDTO> listarFuentesImportacionDiscogs() {
        return DISCOGS_CATALOG_SOURCES.stream()
                .map(source -> new DiscogsCatalogSourceDTO(
                        source.key(), source.label(),
                        discogsImportRowRepository.findDistinctActiveCatalogProductsByJobIds(source.jobIds()).size()
                ))
                .toList();
    }

    private DiscogsCatalogSource source(String key) {
        return DISCOGS_CATALOG_SOURCES.stream()
                .filter(source -> source.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new NegocioException("Fuente Discogs no válida: " + key));
    }

    private record DiscogsCatalogSource(String key, String label, List<Long> jobIds) {}

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
