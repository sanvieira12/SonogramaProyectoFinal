package com.sonograma.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/** Conservatively normalizes supplier codes without removing meaningful punctuation. */
@Component
public class VinylFutureIdentityNormalizer {

    public String normalize(String supplierCode) {
        if (supplierCode == null) return null;
        String normalized = Normalizer.normalize(supplierCode, Normalizer.Form.NFKC)
            .strip()
            .replaceAll("\\s+", " ")
            .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public String operationKey(String invoiceNumber) {
        String normalized = normalize(invoiceNumber);
        return normalized == null ? null : "VINYLFUTURE:" + normalized;
    }
}
