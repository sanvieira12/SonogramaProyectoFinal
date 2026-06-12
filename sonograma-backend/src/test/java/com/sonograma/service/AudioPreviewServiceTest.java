package com.sonograma.service;

import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.CatalogAudioPreview;
import com.sonograma.enums.AudioPreviewStatus;
import com.sonograma.repository.CatalogAudioPreviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioPreviewServiceTest {

    @Mock
    private CatalogAudioPreviewRepository previewRepository;

    private AudioPreviewService service;

    @BeforeEach
    void setUp() {
        service = new AudioPreviewService(previewRepository);
    }

    // ── Test 1: preview rows are persisted linked to the disco ID ────────────

    @Test
    void guardarDesdeTracks_persistsPreviewsLinkedToDisco() {
        Long idDisco = 42L;
        List<TrackInfo> tracks = List.of(
            new TrackInfo("A1", "Track One", "https://deejay.de/stream/A1.mp3"),
            new TrackInfo("B1", "Track Two", "https://deejay.de/stream/B1.mp3")
        );

        when(previewRepository.findByIdDiscoOrderByTrackPosition(idDisco))
            .thenReturn(List.of());
        when(previewRepository.save(any(CatalogAudioPreview.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.guardarDesdeTracks(idDisco, tracks);

        ArgumentCaptor<CatalogAudioPreview> captor = ArgumentCaptor.forClass(CatalogAudioPreview.class);
        verify(previewRepository, times(2)).save(captor.capture());

        List<CatalogAudioPreview> saved = captor.getAllValues();
        saved.forEach(p -> assertEquals(idDisco, p.getIdDisco()));
        assertEquals("A1", saved.get(0).getTrackPosition());
        assertEquals("Track One", saved.get(0).getTrackName());
        assertEquals("https://deejay.de/stream/A1.mp3", saved.get(0).getAudioUrl());
        assertEquals("vinylfuture", saved.get(0).getSource());
        assertEquals(AudioPreviewStatus.FOUND, saved.get(0).getStatus());
    }

    // ── Test 2: tracks with blank mp3Url are skipped ─────────────────────────

    @Test
    void guardarDesdeTracks_skipsTracksWithBlankMp3Url() {
        Long idDisco = 7L;
        List<TrackInfo> tracks = List.of(
            new TrackInfo("A1", "Good Track", "https://deejay.de/stream/ok.mp3"),
            new TrackInfo("A2", "No Audio", null),
            new TrackInfo("A3", "Empty URL", "")
        );

        when(previewRepository.findByIdDiscoOrderByTrackPosition(idDisco))
            .thenReturn(List.of());
        when(previewRepository.save(any(CatalogAudioPreview.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.guardarDesdeTracks(idDisco, tracks);

        // Only the track with a valid URL should be persisted
        verify(previewRepository, times(1)).save(any(CatalogAudioPreview.class));
    }

    @Test
    void guardarDesdeTracks_deduplicatesSameAudioUrl() {
        Long idDisco = 8L;
        List<TrackInfo> tracks = List.of(
            new TrackInfo("A1", "Track", "https://deejay.de/stream/same.mp3"),
            new TrackInfo(null, null, "https://deejay.de/stream/same.mp3")
        );

        when(previewRepository.findByIdDiscoOrderByTrackPosition(idDisco)).thenReturn(List.of());
        when(previewRepository.save(any(CatalogAudioPreview.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarDesdeTracks(idDisco, tracks);

        verify(previewRepository, times(1)).save(any(CatalogAudioPreview.class));
    }

    // ── Test 3: listarPorDisco returns mapped DTOs ───────────────────────────

    @Test
    void listarPorDisco_returnsMappedDTOsInOrder() {
        Long idDisco = 99L;
        CatalogAudioPreview p1 = CatalogAudioPreview.builder()
            .id(1L).idDisco(idDisco).trackPosition("A1").trackName("Alpha")
            .audioUrl("https://ex.com/a.mp3").source("vinylfuture")
            .status(AudioPreviewStatus.FOUND).createdAt(LocalDateTime.now()).build();
        CatalogAudioPreview p2 = CatalogAudioPreview.builder()
            .id(2L).idDisco(idDisco).trackPosition("B1").trackName("Beta")
            .audioUrl("https://ex.com/b.mp3").source("manual")
            .status(AudioPreviewStatus.FOUND).createdAt(LocalDateTime.now()).build();

        when(previewRepository.findByIdDiscoOrderByTrackPosition(idDisco))
            .thenReturn(List.of(p1, p2));

        List<AudioPreviewDTO> result = service.listarPorDisco(idDisco);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("A1", result.get(0).trackPosition());
        assertEquals("vinylfuture", result.get(0).source());
        assertEquals(2L, result.get(1).id());
        assertEquals("manual", result.get(1).source());
    }
}
