package com.sonograma.repository;

import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.EstadoCopiaDisco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscoQrCopyRepository extends JpaRepository<DiscoQrCopy, Long> {

    List<DiscoQrCopy> findByIdDiscoOrderByCopyNumber(Long idDisco);

    List<DiscoQrCopy> findByIdDiscoAndEstadoOrderByCopyNumber(Long idDisco, EstadoCopiaDisco estado);

    long countByIdDiscoAndEstado(Long idDisco, EstadoCopiaDisco estado);

    Optional<DiscoQrCopy> findByCodigoQr(String codigoQr);
}
