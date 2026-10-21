package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaPagoUpdateRequestDTO;
import com.sonograma.dto.PreVentaItemUpdateRequestDTO;
import com.sonograma.dto.PreVentaTransaccionItemRequestDTO;
import com.sonograma.dto.PreVentaTransaccionRequestDTO;
import com.sonograma.entity.*;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.MedioPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreVentaServiceTest {
    private PreVentaRepository preVentas;
    private PreVentaTransaccionRepository transacciones;
    private VentaRepository ventas;
    private DetalleVentaRepository detalles;
    private EnvioRepository envios;
    private DeudaRepository deudas;
    private PreVentaService service;
    private Cliente cliente;

    @BeforeEach void setup() {
        preVentas = mock(PreVentaRepository.class);
        transacciones = mock(PreVentaTransaccionRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        ventas = mock(VentaRepository.class);
        detalles = mock(DetalleVentaRepository.class);
        envios = mock(EnvioRepository.class);
        deudas = mock(DeudaRepository.class);
        service = new PreVentaService(preVentas, transacciones, clientes, discos, ventas, detalles, envios, deudas,
                new ProfitCalculationService(ventas,
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoRepository.class),
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoItemRepository.class),
                        org.mockito.Mockito.mock(CatalogPricingService.class)),
                new BusinessTime(Clock.systemUTC()));
        cliente = new Cliente(); cliente.setIdCliente(3L); cliente.setNombre("Ana"); cliente.setApellido("Pérez"); cliente.setActivo(true);
        when(clientes.findById(3L)).thenReturn(Optional.of(cliente));
        when(clientes.findByIdForUpdate(3L)).thenReturn(Optional.of(cliente));
        AtomicLong itemIds = new AtomicLong(8L);
        when(preVentas.save(any())).thenAnswer(i -> {
            PreVenta p = i.getArgument(0); if (p.getIdPreVenta() == null) p.setIdPreVenta(itemIds.getAndIncrement()); return p;
        });
        when(transacciones.save(any())).thenAnswer(i -> {
            PreVentaTransaccion t = i.getArgument(0);
            if (t.getIdPreVentaTransaccion() == null) t.setIdPreVentaTransaccion(50L);
            return t;
        });
    }

    @Test void creaPendienteSinCodigoAunqueSeSoliciteOtroEstado() {
        PreVentaRequestDTO request = request(); request.setEstado("PAGADA");
        var result = service.crear(request);
        assertThat(result.getEstado()).isEqualTo("PENDIENTE");
        assertThat(result.getCodigoDisco()).isNull();
        verify(preVentas).save(argThat(item -> item.getTransaccion() != null));
        verifyNoInteractions(ventas);
    }

    @Test void normalizaCodigoFutureSinAlterarElValorVisible() {
        PreVentaRequestDTO request = request(); request.setCodigoDisco("  ABC   12-X  ");
        service.crear(request);
        verify(preVentas).save(argThat(p -> "ABC 12-X".equals(p.getCodigoDisco())
            && "abc 12-x".equals(p.getCodigoDiscoNormalizado())));
    }

    @Test void pagarCreaUnSoloMovimientoIdentificadoSinTocarStock() {
        PreVenta p = pending();
        when(preVentas.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        when(ventas.saveAndFlush(any())).thenAnswer(i -> { Venta v = i.getArgument(0); v.setIdVenta(44L); return v; });
        when(detalles.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.marcarPagada(8L);

        assertThat(result.getEstado()).isEqualTo("PAGADA");
        assertThat(result.getIdVentaPago()).isEqualTo(44L);
        verify(ventas, times(1)).saveAndFlush(argThat(v -> "PRE_VENTA".equals(v.getOrigen())
            && v.getIdPreVentaOrigen().equals(8L) && v.getMontoPagado().compareTo(new BigDecimal("1200")) == 0));
        verify(detalles).save(argThat(d -> d.getDisco() == null && d.getCantidad() == 2));
    }

    @Test void rechazaPagoDuplicado() {
        PreVenta p = pending(); p.setEstado("PAGADA");
        when(preVentas.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.marcarPagada(8L)).isInstanceOf(NegocioException.class).hasMessageContaining("ya fue");
        verifyNoInteractions(ventas, detalles);
    }

    @Test void eliminarPendienteNoGeneraVenta() {
        PreVenta p = pending(); when(preVentas.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        service.eliminar(8L);
        verify(preVentas).delete(p); verifyNoInteractions(ventas, detalles);
    }

    @Test void editarPagoSincronizaPreVentaVentaYDetalleSinTocarDeudaNiStock() {
        PreVenta p = pending();
        Venta v = paidVenta(p);
        when(preVentas.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        when(envios.findByVentaIdVenta(44L)).thenReturn(Optional.empty());
        when(deudas.findByVentaIdVenta(44L)).thenReturn(Optional.empty());
        when(ventas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(detalles.save(any())).thenAnswer(i -> i.getArgument(0));

        PreVentaPagoUpdateRequestDTO request = new PreVentaPagoUpdateRequestDTO();
        request.setPrecio(new BigDecimal("1800"));
        request.setCantidad(3);
        request.setFechaPago(java.time.LocalDateTime.of(2026, 9, 4, 15, 30));
        request.setMedioPago("TRANSFERENCIA");
        request.setNumeroRecibo("R-99");
        request.setObservaciones("Pago corregido");

        service.actualizarPago(8L, request);

        assertThat(p.getEstado()).isEqualTo("PAGADA");
        assertThat(p.getPrecio()).isEqualByComparingTo("1800");
        assertThat(p.getCantidad()).isEqualTo(3);
        assertThat(p.getFechaPago()).isEqualTo(request.getFechaPago());
        assertThat(v.getTotalFinal()).isEqualByComparingTo("1800");
        assertThat(v.getMontoPagado()).isEqualByComparingTo("1800");
        assertThat(v.getMontoDeuda()).isZero();
        assertThat(v.getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
        assertThat(v.getMedioPago()).isEqualTo(MedioPago.TRANSFERENCIA);
        assertThat(v.getNumeroRecibo()).isEqualTo("R-99");
        assertThat(v.getDetalles()).singleElement().satisfies(d -> {
            assertThat(d.getCantidad()).isEqualTo(3);
            assertThat(d.getPrecioUnitario()).isEqualByComparingTo("600");
        });
        verify(deudas, never()).save(any());
    }

    @Test void eliminarPagoBorraVentaYDetalleYDevuelvePreVentaAPendiente() {
        PreVenta p = pending();
        p.setEstado("PAGADA");
        p.setFechaPago(java.time.LocalDateTime.of(2026, 9, 4, 15, 30));
        Venta v = paidVenta(p);
        p.setVentaPago(v);
        when(preVentas.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        when(envios.findByVentaIdVenta(44L)).thenReturn(Optional.empty());
        when(deudas.findByVentaIdVenta(44L)).thenReturn(Optional.empty());

        service.eliminarPago(8L);

        assertThat(p.getEstado()).isEqualTo("PENDIENTE");
        assertThat(p.getFechaPago()).isNull();
        assertThat(p.getVentaPago()).isNull();
        assertThat(v.getIdPreVentaOrigen()).isNull();
        verify(detalles).deleteAll(anyList());
        verify(detalles).flush();
        verify(ventas).delete(v);
        verify(ventas).flush();
    }

    @Test void creaTransaccionConVariosItemsAtomicaYCalculaResumen() {
        PreVentaTransaccionRequestDTO request = new PreVentaTransaccionRequestDTO();
        request.setIdCliente(3L);
        request.setIdempotencyKey("create-1");
        request.setItems(List.of(item("Primero", "600", 2), item("Segundo", "900", 1)));

        var result = service.crearTransaccion(request);

        assertThat(result.getIdPreVentaTransaccion()).isEqualTo(50L);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotal()).isEqualByComparingTo("1500");
        assertThat(result.getPendiente()).isEqualByComparingTo("1500");
        assertThat(result.getEstadoPago()).isEqualTo("PENDIENTE");
        verify(preVentas, times(2)).save(any());
    }

    @Test void pagarPendientesOmitePagadosYCreaUnCobroPorCadaPendiente() {
        PreVentaTransaccion tx = transaction();
        PreVenta alreadyPaid = pending();
        paidVenta(alreadyPaid);
        PreVenta unpaid = pending();
        unpaid.setIdPreVenta(9L);
        unpaid.setTransaccion(tx);
        tx.getItems().addAll(List.of(alreadyPaid, unpaid));
        when(transacciones.findByIdForUpdate(50L)).thenReturn(Optional.of(tx));
        when(preVentas.findAllByTransaccionIdForUpdate(50L)).thenReturn(tx.getItems());
        when(ventas.saveAndFlush(any())).thenAnswer(i -> {
            Venta v = i.getArgument(0); v.setIdVenta(45L); return v;
        });
        when(detalles.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.pagarPendientes(50L);

        assertThat(result.getEstadoPago()).isEqualTo("PAGADA");
        assertThat(result.getPendiente()).isZero();
        verify(ventas, times(1)).saveAndFlush(any());
    }

    @Test void rechazaEditarItemPagado() {
        PreVentaTransaccion tx = transaction();
        PreVenta paid = pending();
        paidVenta(paid);
        tx.getItems().add(paid);
        when(transacciones.findByIdForUpdate(50L)).thenReturn(Optional.of(tx));
        when(preVentas.findAllByTransaccionIdForUpdate(50L)).thenReturn(tx.getItems());
        PreVentaItemUpdateRequestDTO request = new PreVentaItemUpdateRequestDTO();
        request.setCantidad(3);
        request.setPrecio(new BigDecimal("1800"));

        assertThatThrownBy(() -> service.actualizarItem(50L, 8L, request))
            .isInstanceOf(NegocioException.class).hasMessageContaining("pagado");
        verify(preVentas, never()).save(argThat(p -> p.getCantidad() == 3));
    }

    private PreVentaTransaccionItemRequestDTO item(String descripcion, String precio, int cantidad) {
        PreVentaTransaccionItemRequestDTO item = new PreVentaTransaccionItemRequestDTO();
        item.setDescripcion(descripcion);
        item.setPrecio(new BigDecimal(precio));
        item.setCantidad(cantidad);
        return item;
    }

    private PreVentaTransaccion transaction() {
        return PreVentaTransaccion.builder().idPreVentaTransaccion(50L).cliente(cliente)
            .fecha(java.time.LocalDate.of(2026, 10, 21)).idempotencyKey("tx-50").build();
    }

    private Venta paidVenta(PreVenta p) {
        DetalleVenta detalle = DetalleVenta.builder()
                .disco(null).precioUnitario(new BigDecimal("600")).cantidad(2).manualItem(true)
                .build();
        Venta v = Venta.builder().idVenta(44L).cliente(cliente).fechaVenta(p.getFechaPago())
                .total(new BigDecimal("1200")).totalFinal(new BigDecimal("1200"))
                .precioVenta(new BigDecimal("1200")).subtotal(new BigDecimal("1200"))
                .montoPagado(new BigDecimal("1200")).montoDeuda(BigDecimal.ZERO)
                .estado(EstadoVenta.COMPLETADA).estadoPago(EstadoPago.PAGADO)
                .origen("PRE_VENTA").idPreVentaOrigen(8L).build();
        detalle.setVenta(v);
        v.getDetalles().add(detalle);
        p.setEstado("PAGADA");
        p.setFechaPago(v.getFechaVenta());
        p.setVentaPago(v);
        return v;
    }

    private PreVentaRequestDTO request() {
        return PreVentaRequestDTO.builder().idCliente(3L).descripcion("Release esperado").cantidad(1)
            .precio(new BigDecimal("650")).build();
    }
    private PreVenta pending() {
        return PreVenta.builder().idPreVenta(8L).transaccion(transaction()).cliente(cliente).descripcionSnap("Álbum largo")
            .codigoDisco("FUT-1").codigoDiscoNormalizado("fut-1").cantidad(2)
            .precio(new BigDecimal("1200")).estado("PENDIENTE").build();
    }
}
