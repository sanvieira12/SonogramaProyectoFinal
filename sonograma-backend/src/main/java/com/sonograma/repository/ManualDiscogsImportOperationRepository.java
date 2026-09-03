package com.sonograma.repository;

import com.sonograma.entity.ManualDiscogsImportOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ManualDiscogsImportOperationRepository
        extends JpaRepository<ManualDiscogsImportOperation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ManualDiscogsImportOperation o WHERE o.operationId = :operationId")
    Optional<ManualDiscogsImportOperation> findByOperationIdForUpdate(@Param("operationId") UUID operationId);
}
