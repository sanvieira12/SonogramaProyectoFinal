package com.sonograma.entity;

import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "disco")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_disco")
    private Long idDisco;

    @Column(name = "codigo_interno")
    private String codigoInterno;

    @Column(name = "codigo_qr", unique = true)
    private String codigoQr;

    @Column(name = "artista", nullable = false)
    private String artista;

    @Column(name = "album", nullable = false)
    private String album;

    @Column(name = "genero")
    private String genero;

    @Column(name = "sello_discografico")
    private String selloDiscografico;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "anio")
    private Integer anio;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicion")
    private CondicionDisco condicion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_disco")
    private TipoDisco tipoDisco;

    @Column(name = "costo", precision = 10, scale = 2)
    private BigDecimal costo;

    @Column(name = "precio_venta", precision = 10, scale = 2)
    private BigDecimal precioVenta;

    // DISPONIBLE, RESERVADO, VENDIDO, SIN_STOCK
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoDisco estado = EstadoDisco.DISPONIBLE;

    @Column(name = "fecha_ingreso")
    private LocalDateTime fechaIngreso;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void onPrePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        fechaIngreso = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void onPreUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}
