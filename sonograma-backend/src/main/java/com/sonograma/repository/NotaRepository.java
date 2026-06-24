package com.sonograma.repository;

import com.sonograma.entity.Nota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotaRepository extends JpaRepository<Nota, Long> {
    List<Nota> findByArchivadaFalseOrderByPinnedDescFechaNotaDescCreatedAtDesc();

    @Query("""
        SELECT n FROM Nota n
        WHERE n.archivada = false
          AND (LOWER(n.titulo) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(n.contenido, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(n.tags, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY n.pinned DESC, n.fechaNota DESC, n.createdAt DESC
        """)
    List<Nota> buscar(String search);
}
