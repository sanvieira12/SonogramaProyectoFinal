package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", nullable = false, unique = true)
    private String nombreUsuario;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "contrasenia", nullable = false)
    private String contrasenia;

    // ADMIN, OPERADOR
    @Column(name = "rol", nullable = false)
    @Builder.Default
    private String rol = "OPERADOR";

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "fecha_alta")
    @Builder.Default
    private LocalDateTime fechaAlta = LocalDateTime.now();

    @Column(name = "fecha_ultimo_acceso")
    private LocalDateTime fechaUltimoAcceso;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
