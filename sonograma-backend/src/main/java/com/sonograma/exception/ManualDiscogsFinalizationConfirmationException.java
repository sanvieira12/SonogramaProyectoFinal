package com.sonograma.exception;

import com.sonograma.dto.ManualDiscogsSourceReconciliationDTO;

import java.util.List;

public class ManualDiscogsFinalizationConfirmationException extends ConflictoNegocioException {
    private final List<String> warnings;
    private final ManualDiscogsSourceReconciliationDTO reconciliation;

    public ManualDiscogsFinalizationConfirmationException(
            List<String> warnings,
            ManualDiscogsSourceReconciliationDTO reconciliation) {
        super(String.join(" ", warnings));
        this.warnings = List.copyOf(warnings);
        this.reconciliation = reconciliation;
    }

    public List<String> getWarnings() { return warnings; }
    public ManualDiscogsSourceReconciliationDTO getReconciliation() { return reconciliation; }
}
