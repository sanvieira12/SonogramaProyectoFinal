package com.sonograma.repository;

import com.sonograma.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByNombre(String nombre);
    List<Cliente> findByNombreContainingIgnoreCase(String nombre);
    Cliente findByCedula(String cedula);
}
