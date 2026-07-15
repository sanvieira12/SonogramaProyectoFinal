package com.sonograma.repository;

import com.sonograma.entity.PreVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PreVentaRepository extends JpaRepository<PreVenta, Long> {
    List<PreVenta> findAllByOrderByFechaDescIdPreVentaDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreVenta p where p.idPreVenta = :id")
    Optional<PreVenta> findByIdForUpdate(@Param("id") Long id);

    List<PreVenta> findByCodigoDiscoNormalizadoAndDiscoIsNullAndEstadoNot(
        String codigoDiscoNormalizado, String estado);
}
