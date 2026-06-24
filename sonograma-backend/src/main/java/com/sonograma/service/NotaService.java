package com.sonograma.service;

import com.sonograma.dto.NotaDTO;
import com.sonograma.dto.NotaRequestDTO;
import com.sonograma.entity.Nota;
import com.sonograma.enums.TipoRelacionNota;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.NotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotaService {

    private final NotaRepository notaRepository;

    @Transactional(readOnly = true)
    public List<NotaDTO> listar(String search) {
        List<Nota> notas = search != null && !search.isBlank()
                ? notaRepository.buscar(search.trim())
                : notaRepository.findByArchivadaFalseOrderByPinnedDescFechaNotaDescCreatedAtDesc();
        return notas.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public NotaDTO obtener(Long id) {
        return notaRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nota", id));
    }

    public NotaDTO crear(NotaRequestDTO request) {
        Nota nota = new Nota();
        aplicar(nota, request, true);
        return toDTO(notaRepository.save(nota));
    }

    public NotaDTO actualizar(Long id, NotaRequestDTO request) {
        Nota nota = notaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nota", id));
        aplicar(nota, request, false);
        return toDTO(notaRepository.save(nota));
    }

    public void archivar(Long id) {
        Nota nota = notaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nota", id));
        nota.setArchivada(true);
        nota.setUpdatedAt(LocalDateTime.now());
        notaRepository.save(nota);
    }

    private void aplicar(Nota nota, NotaRequestDTO request, boolean creando) {
        if (request.getTitulo() != null) nota.setTitulo(textoNulo(request.getTitulo()));
        if (request.getContenido() != null) nota.setContenido(textoNulo(request.getContenido()));
        if (request.getTags() != null) nota.setTags(textoNulo(request.getTags()));
        if (request.getFechaNota() != null) nota.setFechaNota(request.getFechaNota());
        if (request.getTipoRelacion() != null && !request.getTipoRelacion().isBlank()) {
            nota.setTipoRelacion(TipoRelacionNota.valueOf(request.getTipoRelacion()));
        } else if (creando) {
            nota.setTipoRelacion(TipoRelacionNota.GENERAL);
        }
        if (request.getRelatedId() != null || creando) nota.setRelatedId(request.getRelatedId());
        if (request.getPinned() != null) nota.setPinned(request.getPinned());
        if (request.getArchivada() != null) nota.setArchivada(request.getArchivada());

        if (nota.getFechaNota() == null) nota.setFechaNota(LocalDate.now());
        if (nota.getCreatedAt() == null) nota.setCreatedAt(LocalDateTime.now());
        nota.setUpdatedAt(LocalDateTime.now());

        if (nota.getTitulo() == null || nota.getTitulo().isBlank()) {
            throw new NegocioException("El título de la nota es obligatorio");
        }
    }

    private NotaDTO toDTO(Nota nota) {
        return NotaDTO.builder()
                .idNota(nota.getIdNota())
                .titulo(nota.getTitulo())
                .contenido(nota.getContenido())
                .tags(nota.getTags())
                .fechaNota(nota.getFechaNota())
                .createdAt(nota.getCreatedAt())
                .updatedAt(nota.getUpdatedAt())
                .tipoRelacion(nota.getTipoRelacion() != null ? nota.getTipoRelacion().name() : null)
                .relatedId(nota.getRelatedId())
                .pinned(nota.getPinned())
                .archivada(nota.getArchivada())
                .build();
    }

    private String textoNulo(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
