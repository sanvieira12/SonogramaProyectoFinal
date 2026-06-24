package com.sonograma.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteDTO {
    private Long idCliente;
    private String nombre;
    private String apellido;
    private String telefono;
    private String email;
    private String cedula;
    private String instagramUsuario;
    private String direccion;
    private String localidad;
    private String departamento;
    private String sucursalDac;
    private String observaciones;
    private LocalDate ultimaCompra;
    private LocalDateTime fechaAlta;
}
