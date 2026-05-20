package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido")
    private String apellido;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "email")
    private String email;

    @Column(name = "cedula")
    private String cedula;

    @Column(name = "instagram_usuario")
    private String instagramUsuario;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "fecha_alta")
    private LocalDateTime fechaAlta = LocalDateTime.now();
}
