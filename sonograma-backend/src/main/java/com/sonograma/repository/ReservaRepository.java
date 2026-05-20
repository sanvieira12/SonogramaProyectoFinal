package com.sonograma.repository;

import com.sonograma.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByClienteIdCliente(Long idCliente);
    List<Reserva> findByDiscoIdDisco(Long idDisco);
    List<Reserva> findByEstado(String estado);
}
