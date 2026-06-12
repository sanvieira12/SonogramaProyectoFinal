package com.sonograma.repository;

import com.sonograma.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("SELECT p FROM Pedido p ORDER BY p.createdAt DESC")
    List<Pedido> findAllOrderedByCreatedAt();
}
