package com.sonograma.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SucursalDacDTO {
    private String codigo;
    private String nombre;
    private String departamento;
    private String direccion;
}
