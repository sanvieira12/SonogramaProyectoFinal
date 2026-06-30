package com.sonograma.repository;

import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscoRepository extends JpaRepository<Disco, Long> {

    List<Disco> findByEstado(EstadoDisco estado);

    List<Disco> findByCondicion(CondicionDisco condicion);

    List<Disco> findByEstadoOrderByFechaIngresoDesc(EstadoDisco estado);

    Optional<Disco> findByCodigoInterno(String codigoInterno);

    boolean existsByNumeroFacturaCompra(String numeroFacturaCompra);

    Optional<Disco> findByCodigoQr(String codigoQr);

    Optional<Disco> findByDiscogsUrl(String discogsUrl);

    @Query("SELECT d FROM Disco d WHERE " +
           "LOWER(d.artista) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.album) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY d.artista")
    List<Disco> buscarPorArtistaOAlbum(@Param("q") String q);
}
