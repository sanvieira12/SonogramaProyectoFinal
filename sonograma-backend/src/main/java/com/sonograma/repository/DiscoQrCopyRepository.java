package com.sonograma.repository;

import com.sonograma.entity.DiscoQrCopy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscoQrCopyRepository extends JpaRepository<DiscoQrCopy, Long> {

    List<DiscoQrCopy> findByIdDiscoOrderByCopyNumber(Long idDisco);

    Optional<DiscoQrCopy> findByCodigoQr(String codigoQr);
}
