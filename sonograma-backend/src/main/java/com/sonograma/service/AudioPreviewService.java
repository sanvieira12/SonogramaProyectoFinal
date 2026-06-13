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
import java.util.LinkedHashMap;
import java.util.Map;

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
            .filter(p -> "vinylfuture".equals(p.getSource())
                || "discogs-youtube".equals(p.getSource()))
            .forEach(p -> previewRepository.delete(p));

        Map<String, TrackInfo> uniqueTracks = new LinkedHashMap<>();
        tracks.stream()
            .filter(this::hasPlayableUrl)
            .forEach(track -> uniqueTracks.putIfAbsent(trackKey(track), track));

        for (TrackInfo track : uniqueTracks.values()) {
            CatalogAudioPreview preview = CatalogAudioPreview.builder()
                .idDisco(idDisco)
                .trackName(track.name())
                .trackPosition(track.label())
                .audioUrl(track.mp3Url())
                .youtubeUrl(track.youtubeUrl())
                .source(track.mp3Url() != null && !track.mp3Url().isBlank()
                    ? "vinylfuture"
                    : "discogs-youtube")
                .status(AudioPreviewStatus.FOUND)
                .build();
            previewRepository.save(preview);
        }
        log.debug("Guardados {} previews para disco {}", uniqueTracks.size(), idDisco);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AudioPreviewDTO> listarPorDisco(Long idDisco) {
        return previewRepository.findByIdDiscoOrderByTrackPosition(idDisco)
            .stream().map(this::toDTO).toList();
    }

    // ── Manual CRUD ───────────────────────────────────────────────────────────

    public AudioPreviewDTO agregar(Long idDisco, AudioPreviewRequestDTO req) {
        if (isBlank(req.audioUrl()) && isBlank(req.youtubeUrl())) {
            throw new IllegalArgumentException("Ingresá una URL de audio o YouTube");
        }
        CatalogAudioPreview preview = CatalogAudioPreview.builder()
            .idDisco(idDisco)
            .trackName(req.trackName())
            .trackPosition(req.trackPosition())
            .audioUrl(req.audioUrl())
            .youtubeUrl(req.youtubeUrl())
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
            p.getYoutubeUrl(),
            p.getDurationSeconds(),
            p.getSource(),
            p.getStatus().name(),
            p.getCreatedAt()
        );
    }

    private boolean hasPlayableUrl(TrackInfo track) {
        return track != null && (!isBlank(track.mp3Url()) || !isBlank(track.youtubeUrl()));
    }

    private String trackKey(TrackInfo track) {
        return !isBlank(track.mp3Url())
            ? "audio:" + track.mp3Url().strip()
            : "youtube:" + track.youtubeUrl().strip();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
