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
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscoRequestDTO {

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

    @Size(max = 120)
    private String selloDiscografico;

    @Size(max = 1000)
    private String descripcion;

    @Min(value = 1900, message = "El año debe ser mayor a 1900")
    @Max(value = 2100)
    private Integer anio;

    @NotNull(message = "La condición es obligatoria")
    private CondicionDisco condicion;

    @NotNull(message = "El tipo de disco es obligatorio")
    private TipoDisco tipoDisco;

    @DecimalMin(value = "0.0", inclusive = false, message = "El costo debe ser mayor a 0")
    private BigDecimal costo;

    @Size(max = 10)
    private String costoMoneda;

    @Size(max = 255)
    private String numeroFacturaCompra;

    private LocalDate fechaFacturaCompra;

    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    private BigDecimal precioVenta;

    @Size(max = 100)
    private String pais;

    @Size(max = 100)
    private String estilo;

    private String tracklist;

    private String notas;

    @Size(max = 100)
    private String procedencia;

    private String imagenUrl;

    private String previewUrl;

    @Size(max = 500)
    private String discogsUrl;

    @Min(value = 0)
    private Integer cantidadCopias;
}
