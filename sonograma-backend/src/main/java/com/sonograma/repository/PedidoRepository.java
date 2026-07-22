package com.sonograma.repository;

import com.sonograma.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p ORDER BY p.createdAt DESC")
    List<Pedido> findAllOrderedByCreatedAt();

    List<Pedido> findByNumeroFacturaIn(Set<String> numerosFactura);

    List<Pedido> findByNumeroFactura(String numeroFactura);

    List<Pedido> findByOrigenImportacionOrderByCreatedAtDesc(String origenImportacion);

    java.util.Optional<Pedido> findByOrigenImportacionAndNumeroFactura(String origenImportacion, String numeroFactura);
}
