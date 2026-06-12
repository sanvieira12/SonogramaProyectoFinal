package com.sonograma.entity;

import com.sonograma.enums.AudioPreviewStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "catalog_audio_preview")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogAudioPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_disco", nullable = false)
    private Long idDisco;

    @Column(name = "track_name")
    private String trackName;

    @Column(name = "track_position")
    private String trackPosition;

    @Column(name = "audio_url", nullable = false)
    private String audioUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "source")
    @Builder.Default
    private String source = "vinylfuture";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AudioPreviewStatus status = AudioPreviewStatus.FOUND;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
