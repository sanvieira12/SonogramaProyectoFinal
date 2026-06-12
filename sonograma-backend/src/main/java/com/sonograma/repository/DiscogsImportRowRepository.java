package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.DiscogsImportRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiscogsImportRowRepository extends JpaRepository<DiscogsImportRow, Long> {
    List<DiscogsImportRow> findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
            Long jobId,
            Collection<DiscogsImportRowStatus> statuses
    );
}
