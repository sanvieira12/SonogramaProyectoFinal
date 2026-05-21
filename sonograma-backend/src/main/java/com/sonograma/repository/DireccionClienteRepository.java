package com.sonograma.repository;

import com.sonograma.entity.DireccionCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente, Long> {
    List<DireccionCliente> findByClienteIdClienteAndActivaTrueOrderByUltimaUsadaDescFechaAltaDesc(Long idCliente);
}
