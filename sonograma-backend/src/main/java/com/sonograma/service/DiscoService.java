package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoService {

    private final DiscoRepository discoRepository;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final CatalogPricingService catalogPricingService;

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
        return discoRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
        if (nuevoEstado == EstadoDisco.SIN_STOCK || nuevoEstado == EstadoDisco.VENDIDO) {
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
        recalcularEstado(disco);
        return saveWithQr(disco);
    }

    public DiscoResponseDTO cambiarEstadoCopia(Long idDisco, Long idCopia, EstadoCopiaDisco nuevoEstado) {
        Disco disco = discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco));
        qrCopyService.changeCopyStatus(disco, idCopia, nuevoEstado);
        disco.setCantidadCopias((int) qrCopyService.countAvailableCopies(idDisco));
        recalcularEstado(disco);
        return saveWithQr(disco);
    }

    public void eliminarDisco(Long id) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("No se puede dar de baja un disco ya vendido");
        }
        disco.setEstado(EstadoDisco.SIN_STOCK);
        disco.setCantidadCopias(0);
        qrCopyService.synchronizeAvailableCopies(disco, 0);
        saveWithQr(disco);
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
        recalcularEstado(saved);
        return toDTO(discoRepository.save(saved));
    }

    private void recalcularEstado(Disco disco) {
        int disponibles = copiasDisponibles(disco);
        if (disco.getEstado() == EstadoDisco.RESERVADO) {
            if (disponibles == 0) {
                disco.setEstado(EstadoDisco.SIN_STOCK);
            }
            return;
        }
        disco.setEstado(disponibles > 0 ? EstadoDisco.DISPONIBLE : EstadoDisco.SIN_STOCK);
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
