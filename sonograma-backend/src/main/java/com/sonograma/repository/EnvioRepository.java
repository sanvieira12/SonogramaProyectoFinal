package com.sonograma.repository;

import com.sonograma.entity.Envio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface EnvioRepository extends JpaRepository<Envio, Long> {
    Optional<Envio> findByVentaIdVenta(Long idVenta);
    List<Envio> findByEstadoLogistico(String estadoLogistico);
    List<Envio> findByVentaClienteIdClienteOrderByFechaEnvioDesc(Long idCliente);

    @Query("SELECT e FROM Envio e WHERE e.estadoLogistico IN ('PREPARANDO', 'EN_CAMINO') ORDER BY e.fechaEnvio ASC")
    List<Envio> findEnviosPendientes();
}
