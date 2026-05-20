package com.sonograma.repository;

import com.sonograma.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByActivoTrue();

    Optional<Cliente> findByCedulaAndActivoTrue(String cedula);

    boolean existsByCedula(String cedula);

    boolean existsByCedulaAndIdClienteNot(String cedula, Long idCliente);

    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.apellido) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Cliente> buscarActivosPorNombreOApellido(@Param("q") String q);
}
