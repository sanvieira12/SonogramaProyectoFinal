package com.sonograma.repository;

import com.sonograma.entity.ManualDiscogsFinalizationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualDiscogsFinalizationSnapshotRepository
        extends JpaRepository<ManualDiscogsFinalizationSnapshot, Long> {

    Optional<ManualDiscogsFinalizationSnapshot> findByManualBatchId(Long batchId);

    List<ManualDiscogsFinalizationSnapshot>
    findByNormalizedSourceCustomerCodeOrderByFinalizedAtDesc(String normalizedSourceCustomerCode);
}
