package com.sonograma.service;

import com.sonograma.dto.DiscoDTO;
import com.sonograma.dto.DiscoRequest;
import com.sonograma.entity.Disco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoService {

    private final DiscoRepository discoRepository;

    public DiscoDTO crearDisco(DiscoRequest request) {
        Disco disco = DiscoMapper.toEntity(request);
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setFechaIngreso(LocalDateTime.now());
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    @Transactional(readOnly = true)
    public DiscoDTO obtenerPorId(Long id) {
        return discoRepository.findById(id)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
    }

    @Transactional(readOnly = true)
    public DiscoDTO obtenerPorQR(String codigoQr) {
        return discoRepository.findByCodigoQr(codigoQr)
                .map(DiscoMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco no encontrado con QR: " + codigoQr));
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> obtenerTodos() {
        return discoRepository.findAll().stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> obtenerDisponibles() {
        return discoRepository.findByEstadoOrderByFechaIngresoDesc(EstadoDisco.DISPONIBLE).stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> obtenerPorEstado(EstadoDisco estado) {
        return discoRepository.findByEstado(estado).stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> buscar(String q) {
        return discoRepository.buscarPorArtistaOAlbum(q).stream()
                .map(DiscoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public DiscoDTO actualizarDisco(Long id, DiscoRequest request) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        DiscoMapper.updateFromRequest(disco, request);
        disco.setFechaActualizacion(LocalDateTime.now());
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    public DiscoDTO cambiarEstado(Long id, EstadoDisco nuevoEstado) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("No se puede cambiar el estado de un disco ya vendido");
        }
        disco.setEstado(nuevoEstado);
        disco.setFechaActualizacion(LocalDateTime.now());
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    public void eliminarDisco(Long id) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", id));
        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("No se puede dar de baja un disco ya vendido");
        }
        disco.setEstado(EstadoDisco.DESCONTINUADO);
        disco.setFechaActualizacion(LocalDateTime.now());
        discoRepository.save(disco);
    }
}
