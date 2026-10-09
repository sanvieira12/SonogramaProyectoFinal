package com.sonograma.service;

import com.sonograma.dto.StockValuationDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockValuationService {

    private static final String EUR = "EUR";
    private static final String UYU = "UYU";

    private final DiscoRepository discoRepository;
    private final DiscoQrCopyRepository copyRepository;

    @Transactional(readOnly = true)
    public StockValuationDTO current() {
        List<Disco> products = discoRepository.findAll();
        List<Long> productIds = products.stream()
                .map(Disco::getIdDisco)
                .filter(id -> id != null)
                .toList();
        List<DiscoQrCopy> availableCopies = productIds.isEmpty()
                ? List.of()
                : copyRepository.findByIdDiscoInAndEstadoOrderByIdDiscoAscCopyNumberAsc(
                        productIds, EstadoCopiaDisco.DISPONIBLE);
        Map<Long, List<DiscoQrCopy>> copiesByProduct = availableCopies.stream()
                .collect(Collectors.groupingBy(DiscoQrCopy::getIdDisco));

        Totals totals = new Totals();
        for (Disco product : products) {
            List<DiscoQrCopy> copies = copiesByProduct.getOrDefault(
                    product.getIdDisco(), Collections.emptyList());
            if (copies.isEmpty()) continue;
            if (product.getCondicion() == CondicionDisco.NUEVO) {
                addNewProduct(totals, product, copies.size());
            } else if (product.getCondicion() == CondicionDisco.USADO) {
                addUsedCopies(totals, copies);
            }
        }

        return totals.toDto();
    }

    private void addNewProduct(Totals totals, Disco product, int availableCount) {
        totals.availableNewCopies += availableCount;
        BigDecimal quantity = BigDecimal.valueOf(availableCount);

        if (positive(product.getPrecioVenta())) {
            totals.projectedNewUyu = totals.projectedNewUyu.add(product.getPrecioVenta().multiply(quantity));
        } else {
            totals.newAvailableCopiesWithoutSalePrice += availableCount;
        }

        if (!positive(product.getCosto())) {
            totals.newAvailableCopiesWithoutAcquisitionCost += availableCount;
            return;
        }

        BigDecimal acquisitionValue = product.getCosto().multiply(quantity);
        switch (normalizeCurrency(product.getCostoMoneda())) {
            case EUR -> totals.importedNewEur = totals.importedNewEur.add(acquisitionValue);
            case UYU -> totals.importedNewUyu = totals.importedNewUyu.add(acquisitionValue);
            default -> totals.newAvailableCopiesWithUnknownAcquisitionCurrency += availableCount;
        }
    }

    private void addUsedCopies(Totals totals, List<DiscoQrCopy> copies) {
        totals.availableUsedCopies += copies.size();
        for (DiscoQrCopy copy : copies) {
            if (positive(copy.getPrecioVenta())) {
                totals.projectedUsedKnownUyu = totals.projectedUsedKnownUyu.add(copy.getPrecioVenta());
            } else {
                totals.usedAvailableCopiesWithoutPrice++;
            }
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String normalizeCurrency(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static final class Totals {
        private BigDecimal importedNewEur = BigDecimal.ZERO;
        private BigDecimal importedNewUyu = BigDecimal.ZERO;
        private BigDecimal projectedNewUyu = BigDecimal.ZERO;
        private BigDecimal projectedUsedKnownUyu = BigDecimal.ZERO;
        private long availableNewCopies;
        private long availableUsedCopies;
        private long usedAvailableCopiesWithoutPrice;
        private long newAvailableCopiesWithoutSalePrice;
        private long newAvailableCopiesWithoutAcquisitionCost;
        private long newAvailableCopiesWithUnknownAcquisitionCurrency;

        private StockValuationDTO toDto() {
            return new StockValuationDTO(
                    importedNewEur,
                    importedNewUyu,
                    projectedNewUyu,
                    projectedUsedKnownUyu,
                    availableNewCopies,
                    availableUsedCopies,
                    usedAvailableCopiesWithoutPrice,
                    newAvailableCopiesWithoutSalePrice,
                    newAvailableCopiesWithoutAcquisitionCost,
                    newAvailableCopiesWithUnknownAcquisitionCurrency);
        }
    }
}
