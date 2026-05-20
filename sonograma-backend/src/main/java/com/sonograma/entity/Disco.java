package com.sonograma.entity;

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

    @Column(name = "anio")
    private Integer anio;

    // NUEVO, USADO, CONSIGNACION, CATALOGO
    @Column(name = "condicion")
    private String condicion;

    // VINILO, CD, DIGITAL, etc.
    @Column(name = "tipo_disco")
    private String tipoDisco;

    @Column(name = "costo", precision = 10, scale = 2)
    private BigDecimal costo;

    @Column(name = "precio_venta", precision = 10, scale = 2)
    private BigDecimal precioVenta;

    // DISPONIBLE, RESERVADO, VENDIDO, DESCONTINUADO
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private String estado = "DISPONIBLE";

    @Column(name = "fecha_ingreso")
    @Builder.Default
    private LocalDateTime fechaIngreso = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
