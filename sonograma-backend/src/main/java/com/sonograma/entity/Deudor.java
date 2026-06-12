package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deudor")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Deudor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_deudor", nullable = false)
    private String nombreDeudor;

    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "monto_original")
    private String montoOriginal;

    @Column(name = "monto_uyu", precision = 15, scale = 2)
    private BigDecimal montoUyu;

    @Column(name = "fecha_estimada")
    private LocalDate fechaEstimada;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "descripcion_discos", columnDefinition = "TEXT")
    private String descripcionDiscos;

    @Column(name = "estado")
    private String estado = "PENDIENTE";

    @Column(name = "fuente")
    private String fuente = "IMPORTACION_EXCEL";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
