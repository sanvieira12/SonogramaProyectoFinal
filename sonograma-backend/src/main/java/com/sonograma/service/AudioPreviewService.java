package com.sonograma.service;

import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.dto.AudioPreviewRequestDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.CatalogAudioPreview;
import com.sonograma.enums.AudioPreviewStatus;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.CatalogAudioPreviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AudioPreviewService {

    private final CatalogAudioPreviewRepository previewRepository;

    // ── Called during catalog import (from PedidoService) ────────────────────

    public void guardarDesdeTracks(Long idDisco, List<TrackInfo> tracks) {
        if (tracks == null || tracks.isEmpty()) return;

        // Remove existing auto-scraped previews for this disco before re-importing
        List<CatalogAudioPreview> existing = previewRepository.findByIdDiscoOrderByTrackPosition(idDisco);
        existing.stream()
            .filter(p -> "vinylfuture".equals(p.getSource()))
            .forEach(p -> previewRepository.delete(p));

        for (TrackInfo track : tracks) {
            if (track.mp3Url() == null || track.mp3Url().isBlank()) continue;
            CatalogAudioPreview preview = CatalogAudioPreview.builder()
                .idDisco(idDisco)
                .trackName(track.name())
                .trackPosition(track.label())
                .audioUrl(track.mp3Url())
                .source("vinylfuture")
                .status(AudioPreviewStatus.FOUND)
                .build();
            previewRepository.save(preview);
        }
        log.debug("Guardados {} previews para disco {}", tracks.size(), idDisco);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AudioPreviewDTO> listarPorDisco(Long idDisco) {
        return previewRepository.findByIdDiscoOrderByTrackPosition(idDisco)
            .stream().map(this::toDTO).toList();
    }

    // ── Manual CRUD ───────────────────────────────────────────────────────────

    public AudioPreviewDTO agregar(Long idDisco, AudioPreviewRequestDTO req) {
        CatalogAudioPreview preview = CatalogAudioPreview.builder()
            .idDisco(idDisco)
            .trackName(req.trackName())
            .trackPosition(req.trackPosition())
            .audioUrl(req.audioUrl())
            .durationSeconds(req.durationSeconds())
            .source("manual")
            .status(AudioPreviewStatus.FOUND)
            .build();
        return toDTO(previewRepository.save(preview));
    }

    public AudioPreviewDTO actualizarUrl(Long previewId, String audioUrl) {
        CatalogAudioPreview preview = previewRepository.findById(previewId)
            .orElseThrow(() -> new RecursoNoEncontradoException("AudioPreview", previewId));
        preview.setAudioUrl(audioUrl);
        preview.setStatus(AudioPreviewStatus.FOUND);
        preview.setErrorMessage(null);
        return toDTO(previewRepository.save(preview));
    }

    public void eliminar(Long previewId) {
        if (!previewRepository.existsById(previewId)) {
            throw new RecursoNoEncontradoException("AudioPreview", previewId);
        }
        previewRepository.deleteById(previewId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AudioPreviewDTO toDTO(CatalogAudioPreview p) {
        return new AudioPreviewDTO(
            p.getId(),
            p.getIdDisco(),
            p.getTrackName(),
            p.getTrackPosition(),
            p.getAudioUrl(),
            p.getDurationSeconds(),
            p.getSource(),
            p.getStatus().name(),
            p.getCreatedAt()
        );
    }
}
