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
public class ClienteImportResultDTO {
    private String hoja;
    private int totalFilasLeidas;
    private int clientesValidos;
    private int creados;
    private int actualizados;
    private int omitidos;
    private int errores;
    private int filasConIncidencias;
    private List<ClienteImportRowDTO> filas;
}
