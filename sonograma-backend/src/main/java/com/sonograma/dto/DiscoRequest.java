package com.sonograma.dto;

import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.TipoDisco;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscoRequest {

    @Size(max = 50)
    private String codigoInterno;

    @NotBlank(message = "El artista es obligatorio")
    @Size(max = 150)
    private String artista;

    @NotBlank(message = "El álbum es obligatorio")
    @Size(max = 150)
    private String album;

    @Size(max = 50)
    private String genero;

    @Min(value = 1900, message = "El año debe ser mayor a 1900")
    @Max(value = 2100)
    private Integer anio;

    @NotNull(message = "La condición es obligatoria")
    private CondicionDisco condicion;

    @NotNull(message = "El tipo de disco es obligatorio")
    private TipoDisco tipoDisco;

    @DecimalMin(value = "0.0", inclusive = false, message = "El costo debe ser mayor a 0")
    private BigDecimal costo;

    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    private BigDecimal precioVenta;
}
