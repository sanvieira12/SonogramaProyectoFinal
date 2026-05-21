package com.sonograma.service;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;
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

    public DiscoResponseDTO crearDisco(DiscoRequestDTO request) {
        Disco disco = DiscoMapper.toEntity(request);
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setCodigoQr(UUID.randomUUID().toString());
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    @Transactional(readOnly = true)
    public DiscoResponseDTO obtenerPorId(Long id) {
        return discoRepository.findById(id)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
    }

    @Transactional(readOnly = true)
    public DiscoResponseDTO obtenerPorQR(String codigoQr) {
        return discoRepository.findByCodigoQr(codigoQr)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco no encontrado con QR: " + codigoQr));
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> obtenerTodos() {
        return discoRepository.findAll().stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> obtenerDisponibles() {
        return discoRepository.findByEstadoOrderByFechaIngresoDesc(EstadoDisco.DISPONIBLE).stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> obtenerPorEstado(EstadoDisco estado) {
        return discoRepository.findByEstado(estado).stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoResponseDTO> buscar(String q) {
        String query = normalizar(q);
        if (query.isBlank()) {
            return obtenerTodos();
        }
        return discoRepository.findAll().stream()
                .filter(d -> coincide(d, query))
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public DiscoResponseDTO actualizarDisco(Long id, DiscoRequestDTO request) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        DiscoMapper.updateFromRequest(disco, request);
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    public DiscoResponseDTO cambiarEstado(Long id, EstadoDisco nuevoEstado) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("No se puede cambiar el estado de un disco ya vendido");
        }
        disco.setEstado(nuevoEstado);
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    public void eliminarDisco(Long id) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("No se puede dar de baja un disco ya vendido");
        }
        disco.setEstado(EstadoDisco.DESCONTINUADO);
        discoRepository.save(disco);
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

    private boolean contiene(String valor, String query) {
        return valor != null && normalizar(valor).contains(query);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
