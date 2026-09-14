package com.sonograma.service;

import com.sonograma.entity.PagoDeuda;
import org.springframework.stereotype.Component;

/**
 * Canonical inclusion policy for persisted financial debt-payment movements.
 *
 * A payment remains historical income when its debt or related sale later
 * becomes inactive/cancelled. Only an explicit payment annulment removes it
 * from financial reporting.
 */
@Component
public class FinancialMovementPolicy {

    public boolean isReportableDebtPayment(PagoDeuda payment) {
        return payment != null && !Boolean.TRUE.equals(payment.getAnulado());
    }
}
