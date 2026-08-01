package com.sonograma.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    @ToString.Exclude
    private String token;
    private UsuarioDTO usuario;
}
