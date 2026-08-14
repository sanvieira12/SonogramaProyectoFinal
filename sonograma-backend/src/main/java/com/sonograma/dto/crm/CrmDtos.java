package com.sonograma.dto.crm;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.enums.TipoInteresCrm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CrmDtos {
    private CrmDtos() {}

    public record Metricas(
            long cantidadCompras,
            long cantidadDiscos,
            BigDecimal totalGastado,
            BigDecimal promedioPorCompra,
            BigDecimal precioPromedioPorDisco,
            BigDecimal precioMedianoPorDisco,
            BigDecimal precioMinimoPorDisco,
            BigDecimal precioMaximoPorDisco,
            BigDecimal rangoTipicoMinimo,
            BigDecimal rangoTipicoMaximo,
            LocalDateTime primeraCompra,
            LocalDateTime ultimaCompra,
            BigDecimal frecuenciaPromedioDias,
            long comprasUltimos12Meses
    ) {}

    public record Dimension(String valor, long cantidad, BigDecimal porcentaje) {}

    public record Gusto(
            List<Dimension> artistas,
            List<Dimension> generos,
            List<Dimension> estilos,
            List<Dimension> sellos,
            List<Dimension> anios,
            List<Dimension> decadas,
            List<Dimension> formatos,
            List<Dimension> condiciones
    ) {}

    public record Compra(
            Long idVenta,
            Long idDetalle,
            Long idDisco,
            String artista,
            String album,
            String imagenUrl,
            LocalDateTime fechaCompra,
            Integer cantidad,
            BigDecimal precioUnitarioPagado,
            BigDecimal importePagado,
            Boolean itemManual
    ) {}

    public record PerfilCliente(
            ClienteDTO cliente,
            Metricas metricas,
            Gusto perfilHistorico,
            Gusto perfilReciente,
            List<Compra> historialCompras
    ) {}

    public record Interes(
            Long idInteres,
            Long idCliente,
            TipoInteresCrm tipo,
            String texto,
            Boolean activo,
            LocalDateTime fechaCreacion
    ) {}

    public record InteresRequest(
            TipoInteresCrm tipo,
            @NotBlank @Size(max = 500) String texto
    ) {}

    public record InteresEstadoRequest(@NotNull Boolean activo) {}

    public record Recomendacion(
            Long idDisco,
            String artista,
            String album,
            String selloDiscografico,
            Integer anio,
            String genero,
            String estilo,
            String formato,
            String condicion,
            String imagenUrl,
            BigDecimal precio,
            long cantidadDisponible,
            BigDecimal puntaje,
            String nivelAfinidad,
            List<String> razones
    ) {}

    public record ClienteAfin(
            ClienteDTO cliente,
            BigDecimal puntaje,
            String nivelAfinidad,
            List<String> razones
    ) {}
}
