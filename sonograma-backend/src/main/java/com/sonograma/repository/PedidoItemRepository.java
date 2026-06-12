package com.sonograma.repository;

import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.EnrichStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    List<PedidoItem> findByPedidoIdPedido(Long pedidoId);

    List<PedidoItem> findByPedidoIdPedidoAndEnrichStatusIn(Long pedidoId, List<EnrichStatus> statuses);
}
