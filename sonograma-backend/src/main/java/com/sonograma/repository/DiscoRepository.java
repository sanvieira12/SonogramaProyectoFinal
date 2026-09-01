package com.sonograma.repository;

import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DiscoRepository extends JpaRepository<Disco, Long> {

    @Override
    @Query("SELECT d FROM Disco d WHERE d.catalogDeletedAt IS NULL")
    List<Disco> findAll();

    @Override
    @Query("SELECT d FROM Disco d WHERE d.idDisco = :id AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findById(@Param("id") Long id);

    @Override
    @Query("SELECT d FROM Disco d WHERE d.idDisco IN :ids AND d.catalogDeletedAt IS NULL")
    List<Disco> findAllById(@Param("ids") Iterable<Long> ids);

    @Query("SELECT d FROM Disco d WHERE d.idDisco = :id")
    Optional<Disco> findByIdIncludingCatalogDeleted(@Param("id") Long id);

    @Query("SELECT d FROM Disco d WHERE d.estado = :estado AND d.catalogDeletedAt IS NULL")
    List<Disco> findByEstado(@Param("estado") EstadoDisco estado);

    @Query("SELECT d FROM Disco d WHERE d.condicion = :condicion AND d.catalogDeletedAt IS NULL")
    List<Disco> findByCondicion(@Param("condicion") CondicionDisco condicion);

    @Query("SELECT d FROM Disco d WHERE d.estado = :estado AND d.catalogDeletedAt IS NULL ORDER BY d.fechaIngreso DESC")
    List<Disco> findByEstadoOrderByFechaIngresoDesc(@Param("estado") EstadoDisco estado);

    @Query("SELECT d FROM Disco d WHERE d.codigoInterno = :codigoInterno AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findByCodigoInterno(@Param("codigoInterno") String codigoInterno);

    @Query("SELECT d FROM Disco d WHERE LOWER(d.codigoInterno) = LOWER(:codigoInterno) AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findByCodigoInternoIgnoreCase(@Param("codigoInterno") String codigoInterno);

    @Query("SELECT d FROM Disco d WHERE d.vinylFutureSupplierCodeNormalized = :identity AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findByVinylFutureSupplierCodeNormalized(@Param("identity") String identity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Disco d WHERE d.vinylFutureSupplierCodeNormalized = :identity AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findVinylFutureByIdentityForUpdate(@Param("identity") String identity);

    @Query("SELECT d FROM Disco d WHERE d.codigoInterno IS NOT NULL AND d.catalogDeletedAt IS NULL")
    List<Disco> findAllActiveWithCatalogCode();

    boolean existsByNumeroFacturaCompra(String numeroFacturaCompra);

    @Query("SELECT d FROM Disco d WHERE d.codigoQr = :codigoQr AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findByCodigoQr(@Param("codigoQr") String codigoQr);

    @Query("SELECT d FROM Disco d WHERE d.discogsUrl = :discogsUrl AND d.catalogDeletedAt IS NULL")
    Optional<Disco> findByDiscogsUrl(@Param("discogsUrl") String discogsUrl);

    @Query("SELECT d FROM Disco d WHERE d.catalogDeletedAt IS NULL AND LOWER(d.artista) = LOWER(:artista) AND LOWER(d.album) = LOWER(:album)")
    List<Disco> findByArtistaAndAlbumIgnoreCase(@Param("artista") String artista, @Param("album") String album);

    @Query("SELECT d FROM Disco d WHERE d.catalogDeletedAt IS NULL AND (" +
           "LOWER(d.artista) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(d.album) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY d.artista")
    List<Disco> buscarPorArtistaOAlbum(@Param("q") String q);

    @Query("""
        SELECT d, COUNT(c)
        FROM Disco d, DiscoQrCopy c
        WHERE c.idDisco = d.idDisco
          AND c.estado = com.sonograma.enums.EstadoCopiaDisco.DISPONIBLE
          AND d.estado = com.sonograma.enums.EstadoDisco.DISPONIBLE
          AND d.catalogDeletedAt IS NULL
        GROUP BY d
        ORDER BY d.idDisco
        """)
    List<Object[]> findAvailableForCrm();
}
