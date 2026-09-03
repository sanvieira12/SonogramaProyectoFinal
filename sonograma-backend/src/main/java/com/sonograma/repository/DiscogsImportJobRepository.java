package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.enums.DiscogsImportJobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface DiscogsImportJobRepository extends JpaRepository<DiscogsImportJob, Long> {

    List<DiscogsImportJob> findByStatusIn(Collection<DiscogsImportJobStatus> statuses);

    @EntityGraph(attributePaths = {"rows", "rows.importedCatalogProduct"})
    @Query("SELECT j FROM DiscogsImportJob j WHERE j.idDiscogsImportJob = :id")
    Optional<DiscogsImportJob> findDetailedByIdDiscogsImportJob(@Param("id") Long idDiscogsImportJob);
}
