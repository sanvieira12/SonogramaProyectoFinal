package com.sonograma.entity;

import com.sonograma.enums.ManualDiscogsImportOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_discogs_manual_batch")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DiscogsManualBatch manualBatch;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "manual_discogs_import_operation_copy",
            joinColumns = @JoinColumn(name = "operation_id"),
            inverseJoinColumns = @JoinColumn(name = "copy_id"))
    @OrderBy("id ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DiscoQrCopy> resultingCopies = new ArrayList<>();

    @Column(name = "source_customer_code")
    private String sourceCustomerCode;

    @Column(name = "normalized_source_customer_code")
    private String normalizedSourceCustomerCode;

    @Column(name = "submitted_price", precision = 14, scale = 6)
    private BigDecimal submittedPrice;

    @Column(name = "submitted_condition", columnDefinition = "TEXT")
    private String submittedCondition;

    @Column(name = "duplicate_override")
    private Boolean duplicateOverride;

    @Column(name = "duplicate_override_reason", columnDefinition = "TEXT")
    private String duplicateOverrideReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "abandoned_at")
    private LocalDateTime abandonedAt;

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
