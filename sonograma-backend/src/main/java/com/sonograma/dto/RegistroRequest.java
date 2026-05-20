package com.sonograma.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequest {
    private String nombreUsuario;
    private String email;
    private String contrasenia;
}
