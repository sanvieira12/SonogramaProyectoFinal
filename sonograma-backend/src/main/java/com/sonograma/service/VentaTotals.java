package com.sonograma.service;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Venta;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class VentaTotals {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private VentaTotals() {
    }

    static BigDecimal totalProductos(Venta venta) {
        if (venta == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (venta.getDetalles() != null && !venta.getDetalles().isEmpty()) {
            BigDecimal subtotalDetalles = venta.getDetalles().stream()
                    .map(d -> nvl(d.getPrecioUnitario()).multiply(BigDecimal.valueOf(cantidad(d))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return aplicarDescuento(subtotalDetalles, venta.getDescuentoPorcentaje());
        }

        if (venta.getSubtotal() != null) {
            return aplicarDescuento(venta.getSubtotal(), venta.getDescuentoPorcentaje());
        }

        if (venta.getPrecioVenta() != null) {
            return moneda(venta.getPrecioVenta());
        }

        BigDecimal totalGuardado = venta.getTotalFinal() != null ? venta.getTotalFinal() : venta.getTotal();
        if (totalGuardado == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal sinEnvio = totalGuardado.subtract(nvl(venta.getCostoEnvio()));
        if (sinEnvio.compareTo(BigDecimal.ZERO) >= 0) {
            return moneda(sinEnvio);
        }
        return moneda(totalGuardado);
    }

    private static BigDecimal aplicarDescuento(BigDecimal subtotal, BigDecimal descuentoPorcentaje) {
        BigDecimal factor = CIEN.subtract(nvl(descuentoPorcentaje)).divide(CIEN, 4, RoundingMode.HALF_UP);
        return moneda(nvl(subtotal).multiply(factor));
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static int cantidad(DetalleVenta detalle) {
        return detalle.getCantidad() != null && detalle.getCantidad() > 0 ? detalle.getCantidad() : 1;
    }

    private static BigDecimal moneda(BigDecimal valor) {
        return nvl(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
