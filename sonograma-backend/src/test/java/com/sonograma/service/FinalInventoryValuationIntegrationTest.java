package com.sonograma.service;

import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.DiscoQrCopyDetailDTO;
import com.sonograma.dto.StockValuationDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.DisposicionCopiaReason;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FinalInventoryValuationIntegrationTest {

    @Autowired private VentaService ventaService;
    @Autowired private DiscoService discoService;
    @Autowired private DiscoEstadoService discoEstadoService;
    @Autowired private StockValuationService stockValuationService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private DiscogsManualBatchRepository batchRepository;

    @Test
    void newSellCancelAndThreeToZeroDecrementStayCoherentAcrossCatalogQrLibroAndStock() {
        StockValuationDTO baseline = stockValuationService.current();
        Cliente customer = customer("NEW");
        Disco product = product(CondicionDisco.NUEVO, "2000", "10", "EUR");
        List<DiscoQrCopy> copies = List.of(
                copy(product, 1, "new-1", null, null, null),
                copy(product, 2, "new-2", null, null, null),
                copy(product, 3, "new-3", null, null, null));
        synchronize(product);

        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(3);
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getQrCopies())
                .extracting(copy -> copy.codigoQr()).doesNotHaveDuplicates();
        assertValuationDelta(baseline, "30", "0", "6000", "0", 3, 0, 0);

        DiscoQrCopy selected = copies.get(1);
        String selectedQr = selected.getCodigoQr();
        VentaResponseDTO sale = ventaService.registrarVenta(
                saleRequest(customer, product, selected, new BigDecimal("2000")));

        assertThat(copyRepository.findById(selected.getId())).get().satisfies(sold -> {
            assertThat(sold.getEstado()).isEqualTo(EstadoCopiaDisco.VENDIDO);
            assertThat(sold.getCodigoQr()).isEqualTo(selectedQr);
        });
        assertThat(sale.getDetalles().getFirst().getCopyIds()).containsExactly(selected.getId());
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(2);
        assertValuationDelta(baseline, "20", "0", "4000", "0", 2, 0, 0);
        assertThat(ventaService.obtenerLibro(null, null, null, product.getCodigoInterno()))
                .extracting(VentaResponseDTO::getIdVenta).contains(sale.getIdVenta());

        ventaService.cancelarVenta(sale.getIdVenta());

        assertThat(copyRepository.findById(selected.getId())).get().satisfies(restored -> {
            assertThat(restored.getEstado()).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
            assertThat(restored.getCodigoQr()).isEqualTo(selectedQr);
        });
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(3);
        assertValuationDelta(baseline, "30", "0", "6000", "0", 3, 0, 0);
        assertThat(ventaService.obtenerLibro(null, null, null, product.getCodigoInterno())).isEmpty();

        assertNewDecrement(product, copies, baseline, 2, 1, "20", "4000", EstadoDisco.DISPONIBLE);
        assertNewDecrement(product, copies, baseline, 1, 2, "10", "2000", EstadoDisco.DISPONIBLE);
        assertNewDecrement(product, copies, baseline, 0, 3, "0", "0", EstadoDisco.SIN_STOCK);

        assertThat(copyRepository.findAllById(copies.stream().map(DiscoQrCopy::getId).toList()))
                .hasSize(3)
                .extracting(DiscoQrCopy::getCodigoQr)
                .containsExactlyInAnyOrderElementsOf(copies.stream().map(DiscoQrCopy::getCodigoQr).toList());
        assertThatThrownBy(() -> ventaService.registrarVenta(
                saleRequest(customer, product, selected, new BigDecimal("2000"))))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void usedExactSellAndCancelPreserveCommercialIdentityAndValuation() {
        StockValuationDTO baseline = stockValuationService.current();
        Cliente customer = customer("USED-SALE");
        Disco product = product(CondicionDisco.USADO, "9999", "400", "UYU");
        DiscoQrCopy first = copy(product, 1, "used-700", "700", "VG", null);
        DiscoQrCopy second = copy(product, 2, "used-900", "900", "NM", null);
        synchronize(product);

        assertThat(discoService.buscarParaVenta(product.getCodigoInterno(), 20)).singleElement().satisfies(result -> {
            assertThat(result.requiresExactCopySelection()).isTrue();
            assertThat(result.availableCopies()).extracting(copy -> copy.copyId())
                    .containsExactly(first.getId(), second.getId());
        });
        assertValuationDelta(baseline, "0", "0", "0", "1600", 0, 2, 0);

        String exactQr = second.getCodigoQr();
        VentaResponseDTO sale = ventaService.registrarVenta(
                saleRequest(customer, product, second, new BigDecimal("900")));

        assertThat(copyRepository.findById(second.getId())).get().satisfies(sold -> {
            assertThat(sold.getEstado()).isEqualTo(EstadoCopiaDisco.VENDIDO);
            assertThat(sold.getCodigoQr()).isEqualTo(exactQr);
            assertThat(sold.getPrecioVenta()).isEqualByComparingTo("900");
            assertThat(sold.getCondicionFisica()).isEqualTo("NM");
        });
        assertThat(sale.getDetalles().getFirst().getPrecioUnitario()).isEqualByComparingTo("900");
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(1);
        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 1, 0);

        ventaService.cancelarVenta(sale.getIdVenta());

        assertThat(copyRepository.findById(second.getId())).get().satisfies(restored -> {
            assertThat(restored.getEstado()).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
            assertThat(restored.getCodigoQr()).isEqualTo(exactQr);
            assertThat(restored.getPrecioVenta()).isEqualByComparingTo("900");
            assertThat(restored.getCondicionFisica()).isEqualTo("NM");
        });
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(2);
        assertValuationDelta(baseline, "0", "0", "0", "1600", 0, 2, 0);
    }

    @Test
    void usedRetainedRemovalKeepsRowQrCommercialDataProvenanceAndUpdatesStock() {
        StockValuationDTO baseline = stockValuationService.current();
        Cliente customer = customer("USED-REMOVE");
        DiscogsManualBatch batch = batchRepository.save(DiscogsManualBatch.builder()
                .customerCode("FINAL")
                .normalizedCustomerCode("FINAL")
                .status(DiscogsManualBatchStatus.OPEN)
                .build());
        Disco product = product(CondicionDisco.USADO, "9999", null, null);
        DiscoQrCopy first = copy(product, 1, "retained-700", "700", "VG", batch);
        DiscoQrCopy removed = copy(product, 2, "retained-900", "900", "NM", batch);
        synchronize(product);
        String removedQr = removed.getCodigoQr();

        discoService.retirarCopia(product.getIdDisco(), removed.getId(),
                DisposicionCopiaReason.REMOVED_FROM_INVENTORY,
                "Final integration retained removal", "integration-test");

        assertThat(copyRepository.findById(removed.getId())).get().satisfies(copy -> {
            assertThat(copy.getEstado()).isEqualTo(EstadoCopiaDisco.REMOVED);
            assertThat(copy.getCodigoQr()).isEqualTo(removedQr);
            assertThat(copy.getPrecioVenta()).isEqualByComparingTo("900");
            assertThat(copy.getCondicionFisica()).isEqualTo("NM");
            assertThat(copy.getManualDiscogsBatch().getId()).isEqualTo(batch.getId());
            assertThat(copy.getDispositionReason()).isEqualTo(DisposicionCopiaReason.REMOVED_FROM_INVENTORY);
            assertThat(copy.getDispositionNote()).isEqualTo("Final integration retained removal");
            assertThat(copy.getDisposedBy()).isEqualTo("integration-test");
            assertThat(copy.getDisposedAt()).isNotNull();
        });
        assertThat(discoService.obtenerPorId(product.getIdDisco()).getCantidadCopias()).isEqualTo(1);
        assertThat(discoService.obtenerCopias(product.getIdDisco()))
                .extracting(DiscoQrCopyDetailDTO::id).contains(first.getId(), removed.getId());
        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 1, 0);
        assertThat(discoService.buscarParaVenta(product.getCodigoInterno(), 20)).singleElement()
                .satisfies(result -> assertThat(result.availableCopies())
                        .extracting(copy -> copy.copyId()).containsExactly(first.getId()));
        assertThatThrownBy(() -> ventaService.registrarVenta(
                saleRequest(customer, product, removed, new BigDecimal("900"))))
                .isInstanceOf(ConflictoNegocioException.class);
    }

    @Test
    void unknownUsedPriceRemainsNullThroughExactSaleCancellationAndRetainedRemoval() {
        StockValuationDTO baseline = stockValuationService.current();
        Cliente customer = customer("USED-NULL");
        Disco product = product(CondicionDisco.USADO, "9999", null, null);
        copy(product, 1, "known", "700", "VG", null);
        DiscoQrCopy soldAndRestored = copy(product, 2, "unknown-sale", null, "G+", null);
        DiscoQrCopy removed = copy(product, 3, "unknown-remove", null, null, null);
        synchronize(product);

        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 3, 2);

        VentaResponseDTO sale = ventaService.registrarVenta(
                saleRequest(customer, product, soldAndRestored, new BigDecimal("1200")));
        assertThat(copyRepository.findById(soldAndRestored.getId())).get().satisfies(copy -> {
            assertThat(copy.getEstado()).isEqualTo(EstadoCopiaDisco.VENDIDO);
            assertThat(copy.getPrecioVenta()).isNull();
        });
        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 2, 1);

        ventaService.cancelarVenta(sale.getIdVenta());
        assertThat(copyRepository.findById(soldAndRestored.getId())).get().satisfies(copy -> {
            assertThat(copy.getEstado()).isEqualTo(EstadoCopiaDisco.DISPONIBLE);
            assertThat(copy.getPrecioVenta()).isNull();
        });
        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 3, 2);

        discoService.retirarCopia(product.getIdDisco(), removed.getId(),
                DisposicionCopiaReason.DATA_ENTRY_CORRECTION,
                "Unknown exact price remains unknown", "integration-test");
        assertThat(copyRepository.findById(removed.getId())).get().satisfies(copy -> {
            assertThat(copy.getEstado()).isEqualTo(EstadoCopiaDisco.REMOVED);
            assertThat(copy.getPrecioVenta()).isNull();
        });
        assertValuationDelta(baseline, "0", "0", "0", "700", 0, 2, 1);
    }

    private void assertNewDecrement(
            Disco product,
            List<DiscoQrCopy> originalCopies,
            StockValuationDTO baseline,
            int targetAvailable,
            int expectedRemoved,
            String expectedImportedEur,
            String expectedProjectedUyu,
            EstadoDisco expectedState) {
        var response = discoService.actualizarCopias(product.getIdDisco(), targetAvailable);

        assertThat(response.getCantidadCopias()).isEqualTo(targetAvailable);
        assertThat(response.getTotalCopias()).isEqualTo(3);
        assertThat(response.getEstado()).isEqualTo(expectedState.name());
        assertThat(copyRepository.findAllById(originalCopies.stream().map(DiscoQrCopy::getId).toList()))
                .filteredOn(copy -> copy.getEstado() == EstadoCopiaDisco.DISPONIBLE)
                .hasSize(targetAvailable);
        assertThat(copyRepository.findAllById(originalCopies.stream().map(DiscoQrCopy::getId).toList()))
                .filteredOn(copy -> copy.getEstado() == EstadoCopiaDisco.REMOVED)
                .hasSize(expectedRemoved);
        assertValuationDelta(baseline, expectedImportedEur, "0", expectedProjectedUyu, "0",
                targetAvailable, 0, 0);
    }

    private Cliente customer(String label) {
        String suffix = UUID.randomUUID().toString();
        Cliente customer = new Cliente();
        customer.setNombre("Final " + label);
        customer.setCedula("FINAL-" + label + "-" + suffix);
        customer.setActivo(true);
        return clienteRepository.save(customer);
    }

    private Disco product(CondicionDisco condition, String salePrice, String cost, String currency) {
        String suffix = UUID.randomUUID().toString();
        return discoRepository.save(Disco.builder()
                .codigoInterno("FINAL-" + suffix)
                .codigoQr("parent-" + suffix)
                .artista("Final Integration Artist")
                .album("Final Integration " + suffix)
                .condicion(condition)
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(0)
                .precioVenta(decimal(salePrice))
                .costo(decimal(cost))
                .costoMoneda(currency)
                .pricingMode(PricingMode.MANUAL)
                .build());
    }

    private DiscoQrCopy copy(
            Disco product,
            int number,
            String qrLabel,
            String salePrice,
            String physicalCondition,
            DiscogsManualBatch batch) {
        return copyRepository.save(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(number)
                .codigoQr(qrLabel + "-" + UUID.randomUUID())
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .precioVenta(decimal(salePrice))
                .condicionFisica(physicalCondition)
                .manualDiscogsBatch(batch)
                .build());
    }

    private void synchronize(Disco product) {
        discoEstadoService.aplicar(product);
        discoRepository.saveAndFlush(product);
    }

    private VentaRequestDTO saleRequest(
            Cliente customer,
            Disco product,
            DiscoQrCopy copy,
            BigDecimal unitPrice) {
        return VentaRequestDTO.builder()
                .idCliente(customer.getIdCliente())
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(unitPrice)
                .detalles(List.of(DetalleVentaDTO.builder()
                        .idDisco(product.getIdDisco())
                        .copyId(copy.getId())
                        .codigoQr(copy.getCodigoQr())
                        .cantidad(1)
                        .precioUnitario(unitPrice)
                        .build()))
                .build();
    }

    private void assertValuationDelta(
            StockValuationDTO baseline,
            String importedNewEur,
            String importedNewUyu,
            String projectedNewUyu,
            String projectedUsedUyu,
            long availableNew,
            long availableUsed,
            long unknownUsedPrice) {
        StockValuationDTO actual = stockValuationService.current();
        assertThat(actual.importedNewEur().subtract(baseline.importedNewEur()))
                .isEqualByComparingTo(importedNewEur);
        assertThat(actual.importedNewUyu().subtract(baseline.importedNewUyu()))
                .isEqualByComparingTo(importedNewUyu);
        assertThat(actual.projectedNewUyu().subtract(baseline.projectedNewUyu()))
                .isEqualByComparingTo(projectedNewUyu);
        assertThat(actual.projectedUsedKnownUyu().subtract(baseline.projectedUsedKnownUyu()))
                .isEqualByComparingTo(projectedUsedUyu);
        assertThat(actual.availableNewCopies() - baseline.availableNewCopies()).isEqualTo(availableNew);
        assertThat(actual.availableUsedCopies() - baseline.availableUsedCopies()).isEqualTo(availableUsed);
        assertThat(actual.usedAvailableCopiesWithoutPrice()
                - baseline.usedAvailableCopiesWithoutPrice()).isEqualTo(unknownUsedPrice);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
