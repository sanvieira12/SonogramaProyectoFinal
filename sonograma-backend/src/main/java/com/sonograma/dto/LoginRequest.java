package com.sonograma.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String nombreUsuario;
    private String contrasenia;
}
