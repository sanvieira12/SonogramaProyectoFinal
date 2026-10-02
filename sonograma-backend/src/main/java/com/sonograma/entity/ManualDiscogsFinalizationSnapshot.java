package com.sonograma.entity;

import com.sonograma.enums.ManualDiscogsReconciliationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manual_discogs_finalization_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_manual_discogs_snapshot_batch",
                columnNames = "id_discogs_manual_batch"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualDiscogsFinalizationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_discogs_manual_batch", nullable = false, updatable = false)
    private DiscogsManualBatch manualBatch;

    @Column(name = "source_customer_code", nullable = false, updatable = false, length = 255)
    private String sourceCustomerCode;

    @Column(name = "normalized_source_customer_code", nullable = false, updatable = false, length = 255)
    private String normalizedSourceCustomerCode;

    @Column(name = "expected_copy_count", updatable = false)
    private Integer expectedCopyCount;

    @Column(name = "provable_physical_copy_count", nullable = false, updatable = false)
    private Long provablePhysicalCopyCount;

    @Column(name = "available_copy_count", nullable = false, updatable = false)
    private Long availableCopyCount;

    @Column(name = "sold_copy_count", nullable = false, updatable = false)
    private Long soldCopyCount;

    @Column(name = "removed_copy_count", nullable = false, updatable = false)
    private Long removedCopyCount;

    @Column(name = "distinct_release_count", nullable = false, updatable = false)
    private Long distinctReleaseCount;

    @Column(name = "duplicate_release_group_count", nullable = false, updatable = false)
    private Long duplicateReleaseGroupCount;

    @Column(name = "extra_duplicate_copy_count", nullable = false, updatable = false)
    private Long extraDuplicateCopyCount;

    @Column(name = "pending_operation_count", nullable = false, updatable = false)
    private Long pendingOperationCount;

    @Column(name = "completed_operation_count", nullable = false, updatable = false)
    private Long completedOperationCount;

    @Column(name = "abandoned_operation_count", nullable = false, updatable = false)
    private Long abandonedOperationCount;

    @Column(name = "difference", updatable = false)
    private Long difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, updatable = false, length = 40)
    private ManualDiscogsReconciliationStatus reconciliationStatus;

    @Column(name = "finalized_at", nullable = false, updatable = false)
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by", updatable = false, length = 255)
    private String finalizedBy;
}
