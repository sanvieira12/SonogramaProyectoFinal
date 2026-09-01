package com.sonograma.repository;

import com.sonograma.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Set;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p ORDER BY p.createdAt DESC")
    List<Pedido> findAllOrderedByCreatedAt();

    List<Pedido> findByNumeroFacturaIn(Set<String> numerosFactura);

    List<Pedido> findByNumeroFactura(String numeroFactura);

    List<Pedido> findByOrigenImportacionOrderByCreatedAtDesc(String origenImportacion);

    List<Pedido> findByOrigenImportacionAndNumeroFactura(String origenImportacion, String numeroFactura);

    java.util.Optional<Pedido> findByVinylFutureOperationKey(String vinylFutureOperationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.vinylFutureOperationKey = :operationKey")
    java.util.Optional<Pedido> findVinylFutureOperationForUpdate(@Param("operationKey") String operationKey);
}
