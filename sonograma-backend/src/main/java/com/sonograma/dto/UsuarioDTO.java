package com.sonograma.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioDTO {
    private Long idUsuario;
    private String nombreUsuario;
    private String email;
    private String rol;
    private Boolean activo;
}
