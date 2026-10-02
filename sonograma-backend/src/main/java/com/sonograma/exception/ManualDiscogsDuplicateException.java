package com.sonograma.exception;

import com.sonograma.dto.ManualDiscogsDuplicateCopyDTO;

import java.util.List;

public class ManualDiscogsDuplicateException extends ConflictoNegocioException {
    private final String sourceCustomerCode;
    private final Long discogsReleaseId;
    private final List<ManualDiscogsDuplicateCopyDTO> existingCopies;

    public ManualDiscogsDuplicateException(
            String message,
            String sourceCustomerCode,
            Long discogsReleaseId,
            List<ManualDiscogsDuplicateCopyDTO> existingCopies) {
        super(message);
        this.sourceCustomerCode = sourceCustomerCode;
        this.discogsReleaseId = discogsReleaseId;
        this.existingCopies = List.copyOf(existingCopies);
    }

    public String getSourceCustomerCode() { return sourceCustomerCode; }
    public Long getDiscogsReleaseId() { return discogsReleaseId; }
    public List<ManualDiscogsDuplicateCopyDTO> getExistingCopies() { return existingCopies; }
}
