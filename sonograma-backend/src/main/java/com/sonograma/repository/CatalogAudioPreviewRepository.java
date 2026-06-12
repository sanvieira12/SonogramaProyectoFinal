package com.sonograma.repository;

import com.sonograma.entity.CatalogAudioPreview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogAudioPreviewRepository extends JpaRepository<CatalogAudioPreview, Long> {

    List<CatalogAudioPreview> findByIdDiscoOrderByTrackPosition(Long idDisco);

    void deleteByIdDisco(Long idDisco);
}
