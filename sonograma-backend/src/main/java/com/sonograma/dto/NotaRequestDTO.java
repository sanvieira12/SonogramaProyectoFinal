package com.sonograma.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotaRequestDTO {
    private String titulo;
    private String contenido;
    private String tags;
    private LocalDate fechaNota;
    private String tipoRelacion;
    private Long relatedId;
    private Boolean pinned;
    private Boolean archivada;
}
