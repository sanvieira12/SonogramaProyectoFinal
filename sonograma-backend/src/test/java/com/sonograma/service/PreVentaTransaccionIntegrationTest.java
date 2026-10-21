package com.sonograma.service;

import com.sonograma.dto.PreVentaTransaccionItemRequestDTO;
import com.sonograma.dto.PreVentaTransaccionRequestDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.PreVentaRepository;
import com.sonograma.repository.PreVentaTransaccionRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
class PreVentaTransaccionIntegrationTest {

    @Autowired private PreVentaService service;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private PreVentaRepository preVentaRepository;
    @Autowired private PreVentaTransaccionRepository transaccionRepository;
    @Autowired private VentaRepository ventaRepository;

    @Test
    void retryAfterCommitReturnsTheSameTransactionWithoutDuplicatingItems() {
        Cliente cliente = cliente("Retry");
        PreVentaTransaccionRequestDTO request = request(cliente, "integration-retry", "700", "900");

        var first = service.crearTransaccion(request);
        var retry = service.crearTransaccion(request);

        assertThat(retry.getIdPreVentaTransaccion()).isEqualTo(first.getIdPreVentaTransaccion());
        assertThat(retry.getItems()).hasSize(2);
        assertThat(preVentaRepository.countByTransaccionIdPreVentaTransaccion(
            first.getIdPreVentaTransaccion())).isEqualTo(2);
    }

    @Test
    void concurrentCreateRetryReturnsOneTransactionWithoutDuplicatingItems() throws Exception {
        Cliente cliente = cliente("Concurrent create");
        PreVentaTransaccionRequestDTO request = request(cliente, "integration-create-concurrent", "700", "900");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.crearTransaccion(request); });
            var second = executor.submit(() -> { start.await(); return service.crearTransaccion(request); });
            start.countDown();
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(secondResult.getIdPreVentaTransaccion())
                .isEqualTo(firstResult.getIdPreVentaTransaccion());
            assertThat(preVentaRepository.countByTransaccionIdPreVentaTransaccion(
                firstResult.getIdPreVentaTransaccion())).isEqualTo(2);
        }
    }

    @Test
    void separateKeysCreateIndependentTransactionsForTheSameCustomer() {
        Cliente cliente = cliente("Independent transactions");

        var first = service.crearTransaccion(request(cliente, "integration-independent-a", "700", "900"));
        var second = service.crearTransaccion(request(cliente, "integration-independent-b", "700", "900"));

        assertThat(second.getIdPreVentaTransaccion()).isNotEqualTo(first.getIdPreVentaTransaccion());
    }

    @Test
    void addItemRetryUsesTheSameOperationWithoutDuplicatingTheRow() {
        Cliente cliente = cliente("Add retry");
        var created = service.crearTransaccion(request(cliente, "integration-add-parent", "700", "900"));
        PreVentaTransaccionItemRequestDTO add = item("Tercero", "1100");
        add.setIdempotencyKey("integration-add-item");

        var first = service.agregarItem(created.getIdPreVentaTransaccion(), add);
        var retry = service.agregarItem(created.getIdPreVentaTransaccion(), add);

        assertThat(first.getItems()).hasSize(3);
        assertThat(retry.getItems()).hasSize(3);
        assertThat(preVentaRepository.countByTransaccionIdPreVentaTransaccion(
            created.getIdPreVentaTransaccion())).isEqualTo(3);

        add.setPrecio(new BigDecimal("1200"));
        assertThatThrownBy(() -> service.agregarItem(created.getIdPreVentaTransaccion(), add))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("ítem diferente");
    }

    @Test
    void legacyDeleteOfOnlyItemRemovesItsEmptyParent() {
        Cliente cliente = cliente("Legacy delete");
        PreVentaTransaccionRequestDTO request = new PreVentaTransaccionRequestDTO();
        request.setIdCliente(cliente.getIdCliente());
        request.setIdempotencyKey("integration-legacy-delete");
        request.setItems(List.of(item("Único", "700")));
        var created = service.crearTransaccion(request);

        service.eliminar(created.getItems().get(0).getIdPreVenta());

        assertThat(transaccionRepository.findById(created.getIdPreVentaTransaccion())).isEmpty();
    }

    @Test
    void invalidSecondItemRollsBackTheWholeCreation() {
        Cliente cliente = cliente("Rollback create");
        PreVentaTransaccionRequestDTO request = request(cliente, "integration-invalid", "700", "0");

        assertThatThrownBy(() -> service.crearTransaccion(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("importe válido");

        assertThat(transaccionRepository.findByIdempotencyKey("integration-invalid")).isEmpty();
    }

    @Test
    void failureInOneOutstandingItemRollsBackAllNewCollections() {
        Cliente cliente = cliente("Rollback payment");
        var created = service.crearTransaccion(request(cliente, "integration-payment-rollback", "700", "900"));
        var items = transaccionRepository.findDetailedById(created.getIdPreVentaTransaccion()).orElseThrow().getItems();
        items.get(1).setPrecio(BigDecimal.ZERO);
        preVentaRepository.saveAndFlush(items.get(1));
        Set<Long> itemIds = Set.of(items.get(0).getIdPreVenta(), items.get(1).getIdPreVenta());

        assertThatThrownBy(() -> service.pagarPendientes(created.getIdPreVentaTransaccion()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("importe válido");

        assertThat(service.obtenerTransaccion(created.getIdPreVentaTransaccion()).getItems())
            .allSatisfy(item -> {
                assertThat(item.getEstado()).isEqualTo("PENDIENTE");
                assertThat(item.getIdVentaPago()).isNull();
            });
        assertThat(ventaRepository.findAll().stream()
            .filter(venta -> itemIds.contains(venta.getIdPreVentaOrigen())))
            .isEmpty();
    }

    @Test
    void concurrentWholeTransactionPaymentsCreateOneCollectionPerItem() throws Exception {
        Cliente cliente = cliente("Concurrent payment");
        var created = service.crearTransaccion(request(cliente, "integration-payment-concurrent", "700", "900"));
        Set<Long> itemIds = created.getItems().stream().map(item -> item.getIdPreVenta()).collect(java.util.stream.Collectors.toSet());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.pagarPendientes(created.getIdPreVentaTransaccion()); });
            var second = executor.submit(() -> { start.await(); return service.pagarPendientes(created.getIdPreVentaTransaccion()); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getEstadoPago()).isEqualTo("PAGADA");
            assertThat(second.get(10, TimeUnit.SECONDS).getEstadoPago()).isEqualTo("PAGADA");
        }

        assertThat(ventaRepository.findAll().stream()
            .filter(venta -> itemIds.contains(venta.getIdPreVentaOrigen())))
            .hasSize(2);
    }

    private Cliente cliente(String name) {
        Cliente cliente = new Cliente();
        cliente.setNombre(name);
        cliente.setCedula("TX-" + name + "-" + System.nanoTime());
        cliente.setActivo(true);
        return clienteRepository.saveAndFlush(cliente);
    }

    private PreVentaTransaccionRequestDTO request(
            Cliente cliente, String key, String firstPrice, String secondPrice) {
        PreVentaTransaccionRequestDTO request = new PreVentaTransaccionRequestDTO();
        request.setIdCliente(cliente.getIdCliente());
        request.setIdempotencyKey(key);
        request.setItems(List.of(item("Primero", firstPrice), item("Segundo", secondPrice)));
        return request;
    }

    private PreVentaTransaccionItemRequestDTO item(String description, String price) {
        PreVentaTransaccionItemRequestDTO item = new PreVentaTransaccionItemRequestDTO();
        item.setDescripcion(description);
        item.setCantidad(1);
        item.setPrecio(new BigDecimal(price));
        return item;
    }
}
