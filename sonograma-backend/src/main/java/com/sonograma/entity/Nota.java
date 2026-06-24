package com.sonograma.entity;

import com.sonograma.enums.TipoRelacionNota;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota")
    private Long idNota;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "tags")
    private String tags;

    @Column(name = "fecha_nota", nullable = false)
    private LocalDate fechaNota;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_relacion", nullable = false)
    @Builder.Default
    private TipoRelacionNota tipoRelacion = TipoRelacionNota.GENERAL;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "pinned", nullable = false)
    @Builder.Default
    private Boolean pinned = false;

    @Column(name = "archivada", nullable = false)
    @Builder.Default
    private Boolean archivada = false;
}
