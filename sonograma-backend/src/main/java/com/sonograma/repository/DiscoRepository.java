package com.sonograma.repository;

import com.sonograma.entity.Disco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface DiscoRepository extends JpaRepository<Disco, Long> {
    List<Disco> findByArtistaContainingIgnoreCase(String artista);
    List<Disco> findByAlbumContainingIgnoreCase(String album);
    List<Disco> findByEstado(String estado);
    List<Disco> findByCondicion(String condicion);
    Optional<Disco> findByCodigoInterno(String codigoInterno);
    Optional<Disco> findByCodigoQr(String codigoQr);

    @Query("SELECT d FROM Disco d WHERE d.estado = 'DISPONIBLE' ORDER BY d.fechaIngreso DESC")
    List<Disco> findDisponibles();
}
