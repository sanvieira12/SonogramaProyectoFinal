package com.sonograma.service;

import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMovementPolicyTest {

    private final FinancialMovementPolicy policy = new FinancialMovementPolicy();

    @Test
    void onlyExplicitAnnulmentRemovesPaymentFromFinancialHistory() {
        PagoDeuda active = payment(Deuda.builder().activa(true).build(), false);
        PagoDeuda fullyPaid = payment(Deuda.builder().activa(true).estadoPago(EstadoPago.PAGADO).build(), false);
        PagoDeuda inactive = payment(Deuda.builder().activa(false).build(), false);
        PagoDeuda cancelledSale = payment(Deuda.builder()
                .activa(false)
                .venta(Venta.builder().estado(EstadoVenta.CANCELADA).build())
                .build(), false);
        PagoDeuda annulled = payment(Deuda.builder().activa(true).build(), true);
        PagoDeuda manual = payment(Deuda.builder().activa(true).build(), false);
        PagoDeuda saleLinked = payment(Deuda.builder()
                .activa(true)
                .venta(Venta.builder().estado(EstadoVenta.COMPLETADA).build())
                .build(), false);

        assertThat(policy.isReportableDebtPayment(active)).isTrue();
        assertThat(policy.isReportableDebtPayment(fullyPaid)).isTrue();
        assertThat(policy.isReportableDebtPayment(inactive)).isTrue();
        assertThat(policy.isReportableDebtPayment(cancelledSale)).isTrue();
        assertThat(policy.isReportableDebtPayment(annulled)).isFalse();
        assertThat(policy.isReportableDebtPayment(manual)).isTrue();
        assertThat(policy.isReportableDebtPayment(saleLinked)).isTrue();
    }

    private static PagoDeuda payment(Deuda debt, boolean annulled) {
        return PagoDeuda.builder()
                .deuda(debt)
                .monto(new BigDecimal("100"))
                .fechaPago(LocalDate.of(2026, 9, 14))
                .anulado(annulled)
                .build();
    }
}
