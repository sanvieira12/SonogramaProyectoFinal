package com.sonograma.repository;

import com.sonograma.entity.PreVentaTransaccion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PreVentaTransaccionRepository extends JpaRepository<PreVentaTransaccion, Long> {

    Optional<PreVentaTransaccion> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
        DELETE FROM pre_venta_transaccion t
        WHERE t.id_pre_venta_transaccion = :id
          AND NOT EXISTS (
              SELECT 1 FROM pre_venta p
              WHERE p.id_pre_venta_transaccion = t.id_pre_venta_transaccion
          )
        """, nativeQuery = true)
    int deleteIfEmpty(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cliente", "items", "items.disco", "items.ventaPago"})
    List<PreVentaTransaccion> findAllByOrderByFechaDescIdPreVentaTransaccionDesc();

    @EntityGraph(attributePaths = {"cliente", "items", "items.disco", "items.ventaPago"})
    @Query("select distinct t from PreVentaTransaccion t where t.idPreVentaTransaccion = :id")
    Optional<PreVentaTransaccion> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PreVentaTransaccion t where t.idPreVentaTransaccion = :id")
    Optional<PreVentaTransaccion> findByIdForUpdate(@Param("id") Long id);
}
