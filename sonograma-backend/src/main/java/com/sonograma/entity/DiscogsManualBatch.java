package com.sonograma.entity;

import com.sonograma.enums.DiscogsManualBatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent business batch for a manual Discogs customer workflow.
 *
 * This is intentionally separate from DiscogsImportJob and
 * ManualDiscogsImportOperation, which represent technical import concerns.
 */
@Entity
@Table(name = "discogs_manual_batch")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscogsManualBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_discogs_manual_batch")
    private Long id;

    @Column(name = "customer_code", nullable = false, length = 255)
    private String customerCode;

    @Column(name = "normalized_customer_code", nullable = false, length = 255)
    private String normalizedCustomerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DiscogsManualBatchStatus status = DiscogsManualBatchStatus.OPEN;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "manualDiscogsBatch", fetch = FetchType.LAZY)
    @OrderBy("copyNumber ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DiscoQrCopy> copies = new ArrayList<>();

    @PrePersist
    void onPrePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
