package com.sonograma.repository;

import com.sonograma.entity.Deudor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DeudorRepository extends JpaRepository<Deudor, Long> {

    Optional<Deudor> findByNombreDeudorIgnoreCaseAndMontoOriginalAndFechaEstimada(
            String nombreDeudor, String montoOriginal, LocalDate fechaEstimada);
}
