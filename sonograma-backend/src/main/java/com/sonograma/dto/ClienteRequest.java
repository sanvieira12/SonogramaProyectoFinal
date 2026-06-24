package com.sonograma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @Size(max = 100)
    private String apellido;

    @Size(max = 20)
    private String telefono;

    @Email(message = "El email no tiene formato válido")
    @Size(max = 150)
    private String email;

    @Size(max = 20)
    private String cedula;

    @Size(max = 100)
    private String instagramUsuario;

    @Size(max = 255)
    private String direccion;

    @Size(max = 150)
    private String localidad;

    @Size(max = 100)
    private String departamento;

    @Size(max = 180)
    private String sucursalDac;

    private String observaciones;

    private java.time.LocalDate ultimaCompra;
}
