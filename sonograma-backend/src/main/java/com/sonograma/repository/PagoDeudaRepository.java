package com.sonograma.repository;

import com.sonograma.entity.PagoDeuda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoDeudaRepository extends JpaRepository<PagoDeuda, Long> {
    List<PagoDeuda> findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(Long idDeuda);
}
