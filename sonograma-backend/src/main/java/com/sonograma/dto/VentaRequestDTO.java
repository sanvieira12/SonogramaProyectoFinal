package com.sonograma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaRequestDTO {

    @NotNull(message = "El cliente es obligatorio")
    private Long idCliente;

    @NotNull(message = "El disco es obligatorio")
    private Long idDisco;

    @NotBlank(message = "El canal de venta es obligatorio")
    private String canalVenta;

    @NotNull(message = "El total es obligatorio")
    @DecimalMin(value = "0.0", message = "El total no puede ser negativo")
    private BigDecimal total;

    @DecimalMin(value = "0.0", message = "El precio de venta no puede ser negativo")
    private BigDecimal precioVenta;

    @DecimalMin(value = "0.0", message = "El costo de envío no puede ser negativo")
    private BigDecimal costoEnvio;

    @DecimalMin(value = "0.0", message = "El porcentaje de impuesto no puede ser negativo")
    private BigDecimal porcentajeImpuesto;

    @DecimalMin(value = "0.0", message = "Otros costos no pueden ser negativos")
    private BigDecimal otrosCostos;

    @NotBlank(message = "El tipo de entrega es obligatorio")
    private String tipoEntrega;

    private String observaciones;
    private LocalDateTime fechaVenta;
    private Long idDireccionCliente;
    private String direccionEnvio;
    private String departamento;
    private Boolean guardarNuevaDireccion;

    @Size(max = 80)
    private String sucursalDacCodigo;

    @Size(max = 180)
    private String sucursalDacNombre;
}
