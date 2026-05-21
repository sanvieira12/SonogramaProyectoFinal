package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "direccion_cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion")
    private Long idDireccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "direccion", nullable = false)
    private String direccion;

    @Column(name = "departamento")
    private String departamento;

    @Column(name = "referencia")
    private String referencia;

    @Column(name = "fecha_alta")
    @Builder.Default
    private LocalDateTime fechaAlta = LocalDateTime.now();

    @Column(name = "ultima_usada")
    private LocalDateTime ultimaUsada;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
