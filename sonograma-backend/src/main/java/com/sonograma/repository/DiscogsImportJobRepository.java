package com.sonograma.repository;

import com.sonograma.entity.DiscogsImportJob;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscogsImportJobRepository extends JpaRepository<DiscogsImportJob, Long> {

    @EntityGraph(attributePaths = {"rows", "rows.importedCatalogProduct"})
    @Query("SELECT j FROM DiscogsImportJob j WHERE j.idDiscogsImportJob = :id")
    Optional<DiscogsImportJob> findDetailedByIdDiscogsImportJob(@Param("id") Long idDiscogsImportJob);
}
