package com.sonograma.service;

import com.sonograma.dto.ConfiguracionCostosDTO;
import com.sonograma.dto.ResultadoCostoVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.TipoEntrega;
import com.sonograma.exception.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CostosVentaService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    @Value("${sonograma.ventas.impuesto-porcentaje:0}")
    private BigDecimal porcentajeImpuestoDefault;

    @Value("${sonograma.ventas.otros-costos:0}")
    private BigDecimal otrosCostosDefault;

    public ConfiguracionCostosDTO obtenerConfiguracion() {
        return ConfiguracionCostosDTO.builder()
                .porcentajeImpuesto(moneda(porcentajeImpuestoDefault))
                .otrosCostos(moneda(otrosCostosDefault))
                .moneda("UYU")
                .build();
    }

    public ResultadoCostoVentaDTO calcular(Disco disco, VentaRequestDTO dto) {
        BigDecimal costoDisco = moneda(nvl(disco.getCosto()));
        BigDecimal precioVenta = dto.getPrecioVenta() != null
                ? dto.getPrecioVenta()
                : (dto.getTotal() != null ? dto.getTotal() : disco.getPrecioVenta());

        if (precioVenta == null || precioVenta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingresá un precio de venta válido");
        }

        BigDecimal costoEnvio = TipoEntrega.ENVIO.name().equalsIgnoreCase(dto.getTipoEntrega())
                ? nvl(dto.getCostoEnvio())
                : BigDecimal.ZERO;
        BigDecimal porcentajeImpuesto = dto.getPorcentajeImpuesto() != null
                ? dto.getPorcentajeImpuesto()
                : nvl(porcentajeImpuestoDefault);
        BigDecimal otrosCostos = dto.getOtrosCostos() != null
                ? dto.getOtrosCostos()
                : nvl(otrosCostosDefault);

        BigDecimal subtotal = nvl(precioVenta).add(costoEnvio).add(otrosCostos);
        BigDecimal montoImpuesto = subtotal.multiply(porcentajeImpuesto).divide(CIEN, 4, RoundingMode.HALF_UP);
        BigDecimal totalFinal = subtotal.add(montoImpuesto);
        BigDecimal ganancia = totalFinal.subtract(costoDisco).subtract(costoEnvio).subtract(montoImpuesto).subtract(otrosCostos);

        return ResultadoCostoVentaDTO.builder()
                .costoDisco(moneda(costoDisco))
                .precioVenta(moneda(precioVenta))
                .costoEnvio(moneda(costoEnvio))
                .porcentajeImpuesto(moneda(porcentajeImpuesto))
                .montoImpuesto(moneda(montoImpuesto))
                .otrosCostos(moneda(otrosCostos))
                .totalFinal(moneda(totalFinal))
                .gananciaEstimada(moneda(ganancia))
                .build();
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static BigDecimal moneda(BigDecimal valor) {
        return nvl(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
