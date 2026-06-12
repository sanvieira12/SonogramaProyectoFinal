package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeudorDTO {
    private Long id;
    private String nombreDeudor;
    private Long idCliente;
    private String clienteNombre;
    private String montoOriginal;
    private BigDecimal montoUyu;
    private LocalDate fechaEstimada;
    private String notas;
    private String descripcionDiscos;
    private String estado;
    private String fuente;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
