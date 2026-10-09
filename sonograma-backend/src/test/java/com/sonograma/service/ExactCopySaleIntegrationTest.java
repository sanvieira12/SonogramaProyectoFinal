package com.sonograma.service;

import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.DetalleVentaResponseDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.DiscogsManualBatchStatus;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
class ExactCopySaleIntegrationTest {

    @Autowired private VentaService ventaService;
    @Autowired private DiscoService discoService;
    @Autowired private StockValuationService stockValuationService;
    @Autowired private DeudaService deudaService;
    @Autowired private DiscoQrCopyService copyService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private DiscoRepository discoRepository;
    @Autowired private DiscoQrCopyRepository copyRepository;
    @Autowired private DiscogsManualBatchRepository batchRepository;
    @Autowired private DetalleVentaRepository detalleRepository;
    @Autowired private DeudaRepository deudaRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransactionTemplate(transactionManager).execute(status -> createFixture());
    }

    @Test
    void saleLocksAndSellsTheSelectedCopyAndExposesItsSnapshotIdentity() {
        VentaResponseDTO response = ventaService.registrarVenta(request(fixture, fixture.copyBId(), fixture.copyBQr(), null));

        assertThat(copyRepository.findById(fixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(fixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.VENDIDO);
        assertThat(discoRepository.findById(fixture.productId()).orElseThrow().getCodigoQr())
                .isEqualTo(copyRepository.findById(fixture.copyAId()).orElseThrow().getCodigoQr());
        DetalleVentaResponseDTO detail = response.getDetalles().getFirst();
        assertThat(detail.getCopyId()).isEqualTo(fixture.copyBId());
        assertThat(detail.getCopyIds()).containsExactly(fixture.copyBId());
        assertThat(detalleRepository.findById(detail.getIdDetalle()).orElseThrow().getCopyIdsSnapshot())
                .isEqualTo(fixture.copyBId().toString());
    }

    @Test
    void cancellationAndDebtDeletionRestoreOnlyTheSnapshotCopy() {
        VentaResponseDTO cancelled = ventaService.registrarVenta(request(fixture, fixture.copyBId(), fixture.copyBQr(), null));
        ventaService.cancelarVenta(cancelled.getIdVenta());

        assertThat(copyRepository.findById(fixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(fixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);

        Fixture debtFixture = new TransactionTemplate(transactionManager).execute(status -> createFixture());
        VentaResponseDTO indebted = ventaService.registrarVenta(
                request(debtFixture, debtFixture.copyBId(), debtFixture.copyBQr(), BigDecimal.ZERO));
        Long debtId = deudaRepository.findByVentaIdVentaAndActivaTrue(indebted.getIdVenta()).orElseThrow().getIdDeuda();
        deudaService.eliminar(debtId);

        assertThat(copyRepository.findById(debtFixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(debtFixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    @Test
    void cancelledNewCopyRemainsRetainedWhenLaterAggregateDecrementRemovesItFromStock() {
        String suffix = Long.toString(System.nanoTime());
        Cliente customer = new Cliente();
        customer.setNombre("NEW Lifecycle " + suffix);
        customer.setCedula("NEW-LIFE-" + suffix);
        customer.setActivo(true);
        customer = clienteRepository.save(customer);
        Disco product = discoRepository.save(Disco.builder()
                .codigoInterno("NEW-LIFE-" + suffix)
                .codigoQr("new-life-qr-" + suffix)
                .artista("NEW Lifecycle Artist")
                .album("NEW Lifecycle Album")
                .condicion(CondicionDisco.NUEVO)
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(1)
                .precioVenta(new BigDecimal("1000"))
                .pricingMode(PricingMode.AUTO)
                .build());
        DiscoQrCopy copy = copyRepository.save(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco())
                .copyNumber(1)
                .codigoQr("new-life-copy-" + suffix)
                .estado(EstadoCopiaDisco.DISPONIBLE)
                .build());
        assertThat(stockValuationService.current().projectedNewUyu()).isEqualByComparingTo("1000");

        VentaRequestDTO saleRequest = VentaRequestDTO.builder()
                .idCliente(customer.getIdCliente())
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new BigDecimal("1000"))
                .detalles(List.of(DetalleVentaDTO.builder()
                        .idDisco(product.getIdDisco())
                        .cantidad(1)
                        .precioUnitario(new BigDecimal("1000"))
                        .build()))
                .build();
        VentaResponseDTO sale = ventaService.registrarVenta(saleRequest);
        Long detailId = sale.getDetalles().getFirst().getIdDetalle();
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.VENDIDO);
        assertThat(sale.getDetalles().getFirst().getCopyIds()).containsExactly(copy.getId());
        assertThat(stockValuationService.current().projectedNewUyu()).isEqualByComparingTo("0");

        ventaService.cancelarVenta(sale.getIdVenta());
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(stockValuationService.current().projectedNewUyu()).isEqualByComparingTo("1000");

        discoService.actualizarCopias(product.getIdDisco(), 0);

        assertThat(copyRepository.findById(copy.getId())).get().satisfies(retained -> {
            assertThat(retained.getEstado()).isEqualTo(EstadoCopiaDisco.REMOVED);
            assertThat(retained.getCodigoQr()).isEqualTo(copy.getCodigoQr());
        });
        assertThat(detalleRepository.findById(detailId)).get()
                .extracting(detail -> detail.getCopyIdsSnapshot()).isEqualTo(copy.getId().toString());
        assertThat(discoRepository.findById(product.getIdDisco())).get().satisfies(parent -> {
            assertThat(parent.getCantidadCopias()).isZero();
            assertThat(parent.getEstado()).isEqualTo(EstadoDisco.SIN_STOCK);
            assertThat(parent.getCodigoQr()).isEqualTo(copy.getCodigoQr());
        });
        assertThat(stockValuationService.current().projectedNewUyu()).isEqualByComparingTo("0");
    }

    @Test
    void editRoundTripWithoutIdentityFieldsPreservesTheOriginalExactCopy() {
        VentaResponseDTO created = ventaService.registrarVenta(request(fixture, fixture.copyBId(), fixture.copyBQr(), null));
        DetalleVentaResponseDTO original = created.getDetalles().getFirst();

        VentaRequestDTO edit = baseRequest(null);
        edit.setDetalles(List.of(DetalleVentaDTO.builder()
                .idDetalle(original.getIdDetalle())
                .idDisco(fixture.productId())
                .cantidad(1)
                .precioUnitario(new BigDecimal("1300"))
                .build()));
        VentaResponseDTO updated = ventaService.actualizarVenta(created.getIdVenta(), edit);

        assertThat(updated.getDetalles().getFirst().getCopyId()).isEqualTo(fixture.copyBId());
        assertThat(copyRepository.findById(fixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(fixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.VENDIDO);
    }

    @Test
    void concurrentAttemptsCannotSellTheSameCopyTwiceOrTouchItsSibling() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                ready.countDown();
                start.await();
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        Disco product = discoRepository.findById(fixture.productId()).orElseThrow();
                        copyService.reserveCopies(product, 1, fixture.copyBId(), fixture.copyBQr());
                    });
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(copyRepository.findById(fixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(fixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.VENDIDO);
    }

    @Test
    void failedSecondExactReservationRollsBackTheWholeSale() {
        long salesBefore = ventaRepository.count();
        long detailsBefore = detalleRepository.count();
        VentaRequestDTO request = baseRequest(null);
        request.setTotal(new BigDecimal("2350"));
        request.setDetalles(List.of(
                DetalleVentaDTO.builder()
                        .idDisco(fixture.productId())
                        .copyId(fixture.copyAId())
                        .cantidad(1)
                        .precioUnitario(new BigDecimal("1100"))
                        .build(),
                DetalleVentaDTO.builder()
                        .idDisco(fixture.productId())
                        .copyId(fixture.copyBId())
                        .codigoQr("qr-that-does-not-match")
                        .cantidad(1)
                        .precioUnitario(new BigDecimal("1250"))
                        .build()));

        assertThatThrownBy(() -> ventaService.registrarVenta(request))
                .isInstanceOf(com.sonograma.exception.ConflictoNegocioException.class);

        assertThat(ventaRepository.count()).isEqualTo(salesBefore);
        assertThat(detalleRepository.count()).isEqualTo(detailsBefore);
        assertThat(copyRepository.findById(fixture.copyAId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
        assertThat(copyRepository.findById(fixture.copyBId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCopiaDisco.DISPONIBLE);
    }

    private Fixture createFixture() {
        String suffix = Long.toString(System.nanoTime());
        Cliente customer = new Cliente();
        customer.setNombre("Exact Customer " + suffix);
        customer.setCedula("EXACT-" + suffix);
        customer.setActivo(true);
        customer = clienteRepository.save(customer);

        Disco product = discoRepository.save(Disco.builder()
                .codigoInterno("EXACT-" + suffix)
                .codigoQr("aggregate-" + suffix)
                .artista("Exact Artist")
                .album("Exact Album")
                .condicion(CondicionDisco.USADO)
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(2)
                .precioVenta(new BigDecimal("1200"))
                .costo(new BigDecimal("500"))
                .costoMoneda("UYU")
                .pricingMode(PricingMode.AUTO)
                .build());
        DiscogsManualBatch batch = batchRepository.save(DiscogsManualBatch.builder()
                .customerCode("LO")
                .normalizedCustomerCode("LO")
                .status(DiscogsManualBatchStatus.OPEN)
                .build());
        DiscoQrCopy copyA = copyRepository.save(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco()).copyNumber(1).codigoQr("exact-a-" + suffix)
                .estado(EstadoCopiaDisco.DISPONIBLE).manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1100")).condicionFisica("NM").build());
        DiscoQrCopy copyB = copyRepository.save(DiscoQrCopy.builder()
                .idDisco(product.getIdDisco()).copyNumber(2).codigoQr("exact-b-" + suffix)
                .estado(EstadoCopiaDisco.DISPONIBLE).manualDiscogsBatch(batch)
                .precioVenta(new BigDecimal("1250")).condicionFisica("VG+").build());
        return new Fixture(customer.getIdCliente(), product.getIdDisco(), copyA.getId(), copyB.getId(), copyB.getCodigoQr());
    }

    private VentaRequestDTO request(Fixture target, Long copyId, String qr, BigDecimal paid) {
        VentaRequestDTO request = baseRequest(target, paid);
        request.setDetalles(List.of(DetalleVentaDTO.builder()
                .idDisco(target.productId())
                .copyId(copyId)
                .codigoQr(qr)
                .cantidad(1)
                .precioUnitario(new BigDecimal("1250"))
                .build()));
        return request;
    }

    private VentaRequestDTO baseRequest(BigDecimal paid) {
        return baseRequest(fixture, paid);
    }

    private VentaRequestDTO baseRequest(Fixture target, BigDecimal paid) {
        return VentaRequestDTO.builder()
                .idCliente(target.customerId())
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new BigDecimal("1250"))
                .montoPagado(paid)
                .build();
    }

    private record Fixture(Long customerId, Long productId, Long copyAId, Long copyBId, String copyBQr) {}
}
