package com.sonograma.repository;

import com.sonograma.entity.CrmInteresCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CrmInteresClienteRepository extends JpaRepository<CrmInteresCliente, Long> {

    List<CrmInteresCliente> findByClienteIdClienteOrderByActivoDescFechaCreacionDesc(Long clienteId);

    List<CrmInteresCliente> findByClienteIdClienteAndActivoTrueOrderByFechaCreacionDesc(Long clienteId);

    @Query("SELECT i FROM CrmInteresCliente i JOIN FETCH i.cliente c WHERE i.activo = true AND c.activo = true")
    List<CrmInteresCliente> findAllActiveWithCustomer();

    @Query("SELECT i FROM CrmInteresCliente i WHERE i.idInteres = :interestId AND i.cliente.idCliente = :customerId")
    Optional<CrmInteresCliente> findOwned(@Param("customerId") Long customerId,
                                         @Param("interestId") Long interestId);
}
