package com.sonograma.service;

import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Defines the income movements displayed by Libro de ventas.  Dashboard
 * aggregates these same movements; do not introduce a second income rule.
 */
@Component
public class IngresoLibroCalculator {

    public BigDecimal montoVenta(Venta venta) {
        return venta.getMontoPagado() != null
                ? venta.getMontoPagado()
                : VentaTotals.totalProductos(venta);
    }

    public LocalDateTime fechaPago(PagoDeuda pago) {
        return pago.getFechaPago() != null
                ? pago.getFechaPago().atStartOfDay()
                : pago.getCreatedAt();
    }
}
