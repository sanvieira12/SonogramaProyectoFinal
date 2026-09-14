package com.sonograma.service;

import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.repository.DeudaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Defines the income movements displayed by Libro de ventas.  Dashboard
 * aggregates these same movements; do not introduce a second income rule.
 */
@Component
@RequiredArgsConstructor
public class IngresoLibroCalculator {

    private final DeudaRepository deudaRepository;

    public BigDecimal montoVenta(Venta venta) {
        if (venta == null) return BigDecimal.ZERO;

        // Venta.montoPagado is cumulative after a debt payment. For a sale
        // linked to a debt, the sale movement must use only the amount paid
        // when the debt movement was created.
        if (venta.getIdVenta() != null) {
            Optional<Deuda> deuda = deudaRepository.findByVentaIdVenta(venta.getIdVenta());
            if (deuda.isPresent()) {
                return deuda.get().getMontoPagadoInicial() != null
                        ? deuda.get().getMontoPagadoInicial()
                        : BigDecimal.ZERO;
            }
        }

        // Sales without a debt (including fully paid sales) retain their
        // existing behavior: their paid amount is the original sale income.
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
