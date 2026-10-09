package com.sonograma.service;

import com.sonograma.dto.StockValuationDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class StockValuationServiceTest {

    @Autowired private StockValuationService service;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;

    @BeforeEach
    void clean() {
        copyRepository.deleteAll();
        discoRepository.deleteAll();
    }

    @Test
    void newValuationFollowsAvailableRowsAcrossSaleCancellationAndRemoval() {
        Disco product = product(CondicionDisco.NUEVO, "2000", "10", "EUR");
        List<DiscoQrCopy> copies = List.of(
                copy(product, 1, EstadoCopiaDisco.DISPONIBLE, null),
                copy(product, 2, EstadoCopiaDisco.DISPONIBLE, null),
                copy(product, 3, EstadoCopiaDisco.DISPONIBLE, null),
                copy(product, 4, EstadoCopiaDisco.VENDIDO, null),
                copy(product, 5, EstadoCopiaDisco.REMOVED, null));

        assertNewValues(service.current(), "6000", "30", 3);

        copies.get(2).setEstado(EstadoCopiaDisco.VENDIDO);
        copyRepository.save(copies.get(2));
        assertNewValues(service.current(), "4000", "20", 2);

        copies.get(2).setEstado(EstadoCopiaDisco.DISPONIBLE);
        copyRepository.save(copies.get(2));
        assertNewValues(service.current(), "6000", "30", 3);

        copies.get(2).setEstado(EstadoCopiaDisco.REMOVED);
        copyRepository.save(copies.get(2));
        assertNewValues(service.current(), "4000", "20", 2);
    }

    @Test
    void usedValuationSumsOnlyKnownExactAvailablePricesAndNeverParentPriceOrCost() {
        Disco product = product(CondicionDisco.USADO, "9999", "400", "UYU");
        copy(product, 1, EstadoCopiaDisco.DISPONIBLE, "700");
        copy(product, 2, EstadoCopiaDisco.DISPONIBLE, "900");
        copy(product, 3, EstadoCopiaDisco.DISPONIBLE, null);
        copy(product, 4, EstadoCopiaDisco.VENDIDO, "900");
        copy(product, 5, EstadoCopiaDisco.REMOVED, "1100");

        StockValuationDTO valuation = service.current();

        assertThat(valuation.projectedUsedKnownUyu()).isEqualByComparingTo("1600");
        assertThat(valuation.availableUsedCopies()).isEqualTo(3);
        assertThat(valuation.usedAvailableCopiesWithoutPrice()).isEqualTo(1);
        assertThat(valuation.importedNewEur()).isEqualByComparingTo("0");
        assertThat(valuation.importedNewUyu()).isEqualByComparingTo("0");
    }

    @Test
    void usedSoldAndRemovedCopiesContributeZero() {
        Disco product = product(CondicionDisco.USADO, "9999", "400", "UYU");
        copy(product, 1, EstadoCopiaDisco.DISPONIBLE, "700");
        copy(product, 2, EstadoCopiaDisco.VENDIDO, "900");
        copy(product, 3, EstadoCopiaDisco.REMOVED, "1100");

        StockValuationDTO valuation = service.current();

        assertThat(valuation.projectedUsedKnownUyu()).isEqualByComparingTo("700");
        assertThat(valuation.availableUsedCopies()).isEqualTo(1);
        assertThat(valuation.usedAvailableCopiesWithoutPrice()).isZero();
    }

    @Test
    void importedNewValueKeepsEurAndUyuSeparateAndReportsUnvaluedCosts() {
        Disco eur = product(CondicionDisco.NUEVO, "1500", "12.50", " eur ");
        copy(eur, 1, EstadoCopiaDisco.DISPONIBLE, null);
        copy(eur, 2, EstadoCopiaDisco.DISPONIBLE, null);

        Disco uyu = product(CondicionDisco.NUEVO, "2000", "1000", "uyu");
        copy(uyu, 1, EstadoCopiaDisco.DISPONIBLE, null);
        copy(uyu, 2, EstadoCopiaDisco.DISPONIBLE, null);
        copy(uyu, 3, EstadoCopiaDisco.DISPONIBLE, null);

        Disco unknownCurrency = product(CondicionDisco.NUEVO, "500", "8", null);
        copy(unknownCurrency, 1, EstadoCopiaDisco.DISPONIBLE, null);

        Disco missingCostAndPrice = product(CondicionDisco.NUEVO, null, null, null);
        copy(missingCostAndPrice, 1, EstadoCopiaDisco.DISPONIBLE, null);

        StockValuationDTO valuation = service.current();

        assertThat(valuation.importedNewEur()).isEqualByComparingTo("25.00");
        assertThat(valuation.importedNewUyu()).isEqualByComparingTo("3000");
        assertThat(valuation.availableNewCopies()).isEqualTo(7);
        assertThat(valuation.newAvailableCopiesWithUnknownAcquisitionCurrency()).isEqualTo(1);
        assertThat(valuation.newAvailableCopiesWithoutAcquisitionCost()).isEqualTo(1);
        assertThat(valuation.newAvailableCopiesWithoutSalePrice()).isEqualTo(1);
    }

    private void assertNewValues(StockValuationDTO valuation, String projectedUyu, String importedEur, long copies) {
        assertThat(valuation.projectedNewUyu()).isEqualByComparingTo(projectedUyu);
        assertThat(valuation.importedNewEur()).isEqualByComparingTo(importedEur);
        assertThat(valuation.importedNewUyu()).isEqualByComparingTo("0");
        assertThat(valuation.availableNewCopies()).isEqualTo(copies);
    }

    private Disco product(CondicionDisco condition, String salePrice, String cost, String currency) {
        return discoRepository.save(Disco.builder()
                .codigoQr(UUID.randomUUID().toString())
                .artista("Artist " + UUID.randomUUID())
                .album("Album")
                .condicion(condition)
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(0)
                .precioVenta(decimal(salePrice))
                .costo(decimal(cost))
                .costoMoneda(currency)
                .build());
    }

    private DiscoQrCopy copy(
            Disco product,
            int number,
            EstadoCopiaDisco state,
            String salePrice) {
        return copyRepository.save(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(number)
                .codigoQr(UUID.randomUUID().toString())
                .estado(state)
                .precioVenta(decimal(salePrice))
                .build());
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
