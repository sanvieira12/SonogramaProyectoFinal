package com.sonograma.entity;

import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A confirmation identity, not a catalogue identity.  Two different rows may
 * intentionally receive the same Discogs release; the same operation may not.
 */
@Entity
@Table(name = "manual_discogs_import_operation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualDiscogsImportOperation {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "discogs_release_id", nullable = false)
    private Long discogsReleaseId;

    @Column(name = "requested_copies", nullable = false)
    private Integer requestedCopies;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ManualDiscogsImportOperationStatus status;

    @Column(name = "resulting_product_id")
    private Long resultingProductId;

    @Column(name = "result_type", length = 40)
    private String resultType;

    @Column(name = "available_copies")
    private Integer availableCopies;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onPrePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
