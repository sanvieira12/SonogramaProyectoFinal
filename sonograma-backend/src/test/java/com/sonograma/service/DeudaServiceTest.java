package com.sonograma.service;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.dto.PagoDeudaDTO;
import com.sonograma.enums.EstadoPago;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeudaServiceTest {

    @Mock DeudaRepository deudaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock PagoDeudaRepository pagoDeudaRepository;
    private DeudaService service;

    @BeforeEach
    void setUp() {
        service = new DeudaService(deudaRepository, clienteRepository, pagoDeudaRepository);
    }

    @Test
    void registraPagosSeparadosConBoletaOpcionalYEvitaDuplicadosPorReintento() {
        Deuda deuda = Deuda.builder().idDeuda(1L).activa(true)
                .montoTotal(new BigDecimal("3000")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(BigDecimal.ZERO).montoPendiente(new BigDecimal("3000"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        List<PagoDeuda> pagos = new ArrayList<>();
        when(deudaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(1L))
                .thenAnswer(invocation -> List.copyOf(pagos));
        when(pagoDeudaRepository.findByDeudaIdDeudaAndIdempotencyKey(any(Long.class), any(String.class)))
                .thenAnswer(invocation -> pagos.stream()
                        .filter(p -> invocation.getArgument(1).equals(p.getIdempotencyKey()))
                        .findFirst());
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> {
            PagoDeuda pago = invocation.getArgument(0);
            pago.setIdPagoDeuda((long) pagos.size() + 1);
            pagos.add(pago);
            return pago;
        });
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var parcialConBoleta = service.registrarPago(1L, new BigDecimal("1000"), null, "1258", "payment-1");
        var parcialSinBoleta = service.registrarPago(1L, new BigDecimal("500"), null, "", "payment-2");
        var completoConBoleta = service.registrarPago(1L, new BigDecimal("1500"), null, "1320", "payment-3");

        assertThat(parcialConBoleta.getMontoPendiente()).isEqualByComparingTo("2000");
        assertThat(parcialSinBoleta.getMontoPendiente()).isEqualByComparingTo("1500");
        assertThat(completoConBoleta.getMontoPendiente()).isEqualByComparingTo("0");
        assertThat(completoConBoleta.getEstadoPago()).isEqualTo("PAGADO");
        assertThat(pagos).extracting(PagoDeuda::getNumeroRecibo)
                .containsExactly("1258", null, "1320");
        assertThat(completoConBoleta.getPagos()).extracting(PagoDeudaDTO::getNumeroRecibo)
                .containsExactly("1258", null, "1320");

        service.registrarPago(1L, new BigDecimal("1500"), null, "1320", "payment-3");

        assertThat(pagos).hasSize(3);
    }

    @Test
    void eliminarPagoRestauraSaldoEstadoFechaYVentaVinculada() {
        Venta venta = Venta.builder().montoPagado(new BigDecimal("7000"))
                .montoDeuda(new BigDecimal("1000")).estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(1L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("8000")).montoPagado(new BigDecimal("7000"))
                .montoPendiente(new BigDecimal("1000")).estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda eliminado = PagoDeuda.builder().idPagoDeuda(10L).deuda(deuda)
                .monto(new BigDecimal("5000")).fechaPago(LocalDate.of(2026, 7, 10)).build();
        PagoDeuda anterior = PagoDeuda.builder().idPagoDeuda(9L).deuda(deuda)
                .monto(new BigDecimal("2000")).fechaPago(LocalDate.of(2026, 7, 8)).build();
        when(deudaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(10L)).thenReturn(Optional.of(eliminado));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(1L)).thenReturn(List.of(anterior));

        service.eliminarPago(10L);

        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("2000");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("6000");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
        assertThat(deuda.getFechaUltimoPago()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("2000");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("6000");
        verify(pagoDeudaRepository).delete(eliminado);
        verify(deudaRepository).save(deuda);
    }

    @Test
    void eliminarPagoCompletoReabreLaDeuda() {
        Venta venta = Venta.builder().montoPagado(new BigDecimal("3000"))
                .montoDeuda(BigDecimal.ZERO).estadoPago(EstadoPago.PAGADO).build();
        Deuda deuda = Deuda.builder().idDeuda(2L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("3000")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(new BigDecimal("3000")).montoPendiente(BigDecimal.ZERO)
                .estadoPago(EstadoPago.PAGADO).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(20L).deuda(deuda)
                .monto(new BigDecimal("3000")).fechaPago(LocalDate.of(2026, 7, 11))
                .numeroRecibo("BOLETA-20").build();

        when(deudaRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(20L)).thenReturn(Optional.of(pago));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(2L))
                .thenReturn(List.of());

        service.eliminarPago(20L);

        verify(pagoDeudaRepository).delete(pago);
        assertThat(deuda.getMontoPagado()).isZero();
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("3000");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(venta.getMontoPagado()).isZero();
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("3000");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void eliminarPagoRepetidoFallaSinModificarLaDeuda() {
        Deuda deuda = Deuda.builder().idDeuda(1L).activa(true)
                .montoTotal(new BigDecimal("8000")).montoPagado(new BigDecimal("5000"))
                .montoPendiente(new BigDecimal("3000")).estadoPago(EstadoPago.PARCIAL).build();
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarPago(10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("3000");
    }

    @Test
    void noPermiteAnularDosVecesElMismoPago() {
        Deuda deuda = Deuda.builder().idDeuda(3L).activa(true)
                .montoTotal(new BigDecimal("1000")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(new BigDecimal("1000")).montoPendiente(BigDecimal.ZERO)
                .estadoPago(EstadoPago.PAGADO).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(30L).deuda(deuda)
                .monto(new BigDecimal("1000")).anulado(true).build();
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(30L))
                .thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.eliminarPago(30L))
                .isInstanceOf(com.sonograma.exception.NegocioException.class)
                .hasMessage("El pago de deuda ya fue anulado");
        assertThat(deuda.getMontoPendiente()).isZero();
    }

    @Test
    void consolidaMovimientosPorIdDeClienteYCalculaElSaldoDesdePagos() {
        Cliente cliente = cliente(7L, "Mismo Nombre");
        Deuda primera = movimiento(1L, cliente, "1500", "0", "9999");
        Deuda segunda = movimiento(2L, cliente, "800", "0", "0");
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(50L).deuda(primera)
                .monto(new BigDecimal("500")).fechaPago(LocalDate.of(2026, 7, 1)).build();
        when(deudaRepository.findAllByActivaTrueOrderByFechaDeudaDescFechaCreacionDesc())
                .thenReturn(List.of(segunda, primera));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(1L)).thenReturn(List.of(pago));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(2L)).thenReturn(List.of());

        var rows = service.obtenerPendientes(null);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getIdCliente()).isEqualTo(7L);
            assertThat(row.getMontoPendiente()).isEqualByComparingTo("1800");
            assertThat(row.getCantidadMovimientos()).isEqualTo(2);
            assertThat(row.getMovimientos()).hasSize(2);
            assertThat(row.getMovimientos().get(0).getMontoPendiente()).isEqualByComparingTo("800");
            assertThat(row.getMovimientos().get(1).getMontoPendiente()).isEqualByComparingTo("1000");
        });
    }

    @Test
    void clientesConElMismoNombrePeroDistintoIdNoSeAgrupan() {
        Cliente primero = cliente(8L, "Ana Pérez");
        Cliente segundo = cliente(9L, "Ana Pérez");
        Deuda deudaPrimera = movimiento(3L, primero, "100", "0", "0");
        Deuda deudaSegunda = movimiento(4L, segundo, "200", "0", "0");
        when(deudaRepository.findAllByActivaTrueOrderByFechaDeudaDescFechaCreacionDesc())
                .thenReturn(List.of(deudaPrimera, deudaSegunda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(3L)).thenReturn(List.of());
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(4L)).thenReturn(List.of());

        var rows = service.obtenerPendientes(null);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting("idCliente").containsExactlyInAnyOrder(8L, 9L);
    }

    private static Deuda movimiento(Long id, Cliente cliente, String total, String inicial, String cached) {
        return Deuda.builder().idDeuda(id).cliente(cliente).activa(true)
                .montoTotal(new BigDecimal(total)).montoPagadoInicial(new BigDecimal(inicial))
                .montoPagado(new BigDecimal(cached)).montoPendiente(new BigDecimal(total))
                .estadoPago(EstadoPago.PENDIENTE).fechaDeuda(LocalDate.of(2026, 7, 10)).build();
    }

    private static Cliente cliente(Long id, String nombre) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(id);
        cliente.setNombre(nombre);
        cliente.setActivo(true);
        return cliente;
    }
}
