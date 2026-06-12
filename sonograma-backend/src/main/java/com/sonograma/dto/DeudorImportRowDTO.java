package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeudorImportRowDTO {
    private int fila;
    private String nombreDeudor;
    private String montoOriginal;
    private BigDecimal montoUyu;
    private LocalDate fechaEstimada;
    private String notas;
    private String descripcionDiscos;
    private String estado;
    private String mensaje;
}
