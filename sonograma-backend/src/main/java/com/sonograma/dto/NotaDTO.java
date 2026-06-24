package com.sonograma.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaDTO {
    private Long idNota;
    private String titulo;
    private String contenido;
    private String tags;
    private LocalDate fechaNota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String tipoRelacion;
    private Long relatedId;
    private Boolean pinned;
    private Boolean archivada;
}
