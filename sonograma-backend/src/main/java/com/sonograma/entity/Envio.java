package com.sonograma.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "envio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_envio")
    private Long idEnvio;

    @OneToOne
    @JoinColumn(name = "id_venta", nullable = false, unique = true)
    private Venta venta;

    @Column(name = "direccion_envio", nullable = false)
    private String direccionEnvio;

    @Column(name = "departamento")
    private String departamento;

    @Column(name = "sucursal_dac_codigo")
    private String sucursalDacCodigo;

    @Column(name = "sucursal_dac_nombre")
    private String sucursalDacNombre;

    @Column(name = "costo_envio", precision = 10, scale = 2)
    private BigDecimal costoEnvio;

    // PREPARANDO, EN_CAMINO, ENTREGADO, DEVUELTO
    @Column(name = "estado_logistico")
    @Builder.Default
    private String estadoLogistico = "PREPARANDO";

    @Column(name = "numero_seguimiento")
    private String numeroSeguimiento;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "observaciones")
    private String observaciones;
}
