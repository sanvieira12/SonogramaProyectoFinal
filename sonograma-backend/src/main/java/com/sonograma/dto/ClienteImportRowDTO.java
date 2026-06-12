package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteImportRowDTO {
    private int filaExcel;
    private String nombre;
    private String cedula;
    private String telefono;
    private String estado;
    private String mensaje;
}
