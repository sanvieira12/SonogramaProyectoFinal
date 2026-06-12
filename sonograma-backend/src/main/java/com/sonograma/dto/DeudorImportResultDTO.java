package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeudorImportResultDTO {
    private int totalFilas;
    private int filasVacias;
    private int detectados;
    private int creados;
    private int actualizados;
    private int omitidos;
    private int errores;
    private List<DeudorImportRowDTO> filas;
}
