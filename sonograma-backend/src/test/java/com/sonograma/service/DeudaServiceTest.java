package com.sonograma.service;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(pagoDeudaRepository.findByIdPagoDeudaAndDeudaIdDeuda(10L, 1L)).thenReturn(Optional.of(eliminado));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(1L)).thenReturn(List.of(anterior));

        service.eliminarPago(1L, 10L);

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
    void eliminarPagoRepetidoFallaSinModificarLaDeuda() {
        Deuda deuda = Deuda.builder().idDeuda(1L).activa(true)
                .montoTotal(new BigDecimal("8000")).montoPagado(new BigDecimal("5000"))
                .montoPendiente(new BigDecimal("3000")).estadoPago(EstadoPago.PARCIAL).build();
        when(deudaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByIdPagoDeudaAndDeudaIdDeuda(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarPago(1L, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("3000");
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
