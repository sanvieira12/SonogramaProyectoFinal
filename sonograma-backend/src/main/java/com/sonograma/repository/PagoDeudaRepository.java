package com.sonograma.repository;

import com.sonograma.entity.PagoDeuda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoDeudaRepository extends JpaRepository<PagoDeuda, Long> {
    List<PagoDeuda> findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(Long idDeuda);

    Optional<PagoDeuda> findByIdPagoDeudaAndDeudaIdDeuda(Long idPagoDeuda, Long idDeuda);

    Optional<PagoDeuda> findByDeudaIdDeudaAndIdempotencyKey(Long idDeuda, String idempotencyKey);
}
