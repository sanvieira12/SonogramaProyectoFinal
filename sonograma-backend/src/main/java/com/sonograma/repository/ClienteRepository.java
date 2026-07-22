package com.sonograma.repository;

import com.sonograma.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByActivoTrue();

    Optional<Cliente> findByCedulaAndActivoTrue(String cedula);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.idCliente = :id")
    Optional<Cliente> findByIdForUpdate(@Param("id") Long id);

    Optional<Cliente> findByCedula(String cedula);

    boolean existsByCedula(String cedula);

    boolean existsByCedulaAndIdClienteNot(String cedula, Long idCliente);

    boolean existsByCedulaAndActivoTrue(String cedula);

    boolean existsByCedulaAndActivoTrueAndIdClienteNot(String cedula, Long idCliente);

    Optional<Cliente> findByTelefonoIgnoreCase(String telefono);

    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.apellido) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Cliente> buscarActivosPorNombreOApellido(@Param("q") String q);
}
