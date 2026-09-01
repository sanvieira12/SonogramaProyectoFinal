package com.sonograma.repository;

import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.EnrichStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    List<PedidoItem> findByPedidoIdPedido(Long pedidoId);

    java.util.Optional<PedidoItem> findFirstByDiscoIdDiscoOrderByIdPedidoItemDesc(Long idDisco);

    List<PedidoItem> findByPedidoIdPedidoAndEnrichStatusIn(Long pedidoId, List<EnrichStatus> statuses);

    @Query("SELECT i FROM PedidoItem i JOIN FETCH i.pedido p WHERE p.origenImportacion = 'vinylfuture' AND i.estadoLectura = 'REVIEW_REQUIRED' AND i.disco IS NULL ORDER BY p.createdAt DESC, i.lineaFactura")
    List<PedidoItem> findPendingVinylFutureReviewItems();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PedidoItem i JOIN FETCH i.pedido WHERE i.idPedidoItem = :id")
    java.util.Optional<PedidoItem> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT i FROM PedidoItem i JOIN FETCH i.pedido WHERE i.idPedidoItem = :id")
    java.util.Optional<PedidoItem> findByIdWithPedido(@Param("id") Long id);
}
