package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.enums.DiscogsImportJobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface DiscogsImportJobRepository extends JpaRepository<DiscogsImportJob, Long> {

    List<DiscogsImportJob> findByStatusIn(Collection<DiscogsImportJobStatus> statuses);

    /**
     * Serializes receipt decisions for all jobs representing the same source
     * bytes, including concurrent jobs created from the same workbook.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM DiscogsImportJob j WHERE j.sourceFingerprint = :fingerprint ORDER BY j.idDiscogsImportJob")
    List<DiscogsImportJob> findBySourceFingerprintForUpdate(@Param("fingerprint") String fingerprint);

    @EntityGraph(attributePaths = {"rows", "rows.importedCatalogProduct"})
    @Query("SELECT j FROM DiscogsImportJob j WHERE j.idDiscogsImportJob = :id")
    Optional<DiscogsImportJob> findDetailedByIdDiscogsImportJob(@Param("id") Long idDiscogsImportJob);
}
