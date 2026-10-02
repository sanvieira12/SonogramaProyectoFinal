package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manual_discogs_source_reconciliation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_manual_discogs_reconciliation_source",
                columnNames = "normalized_source_customer_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualDiscogsSourceReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_customer_code", nullable = false, length = 255)
    private String sourceCustomerCode;

    @Column(name = "normalized_source_customer_code", nullable = false, length = 255)
    private String normalizedSourceCustomerCode;

    @Column(name = "expected_copy_count")
    private Integer expectedCopyCount;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "expected_count_change_reason", columnDefinition = "TEXT")
    private String expectedCountChangeReason;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
