package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaPagoUpdateRequestDTO;
import com.sonograma.entity.*;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.MedioPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreVentaServiceTest {
    private PreVentaRepository preVentas;
    private VentaRepository ventas;
    private DetalleVentaRepository detalles;
    private EnvioRepository envios;
    private DeudaRepository deudas;
    private PreVentaService service;
    private Cliente cliente;

    @BeforeEach void setup() {
        preVentas = mock(PreVentaRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        ventas = mock(VentaRepository.class);
        detalles = mock(DetalleVentaRepository.class);
        envios = mock(EnvioRepository.class);
        deudas = mock(DeudaRepository.class);
        service = new PreVentaService(preVentas, clientes, discos, ventas, detalles, envios, deudas,
                new ProfitCalculationService(ventas,
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoRepository.class),
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoItemRepository.class),
                        org.mockito.Mockito.mock(CatalogPricingService.class)));
        cliente = new Cliente(); cliente.setIdCliente(3L); cliente.setNombre("Ana"); cliente.setApellido("Pérez"); cliente.setActivo(true);
        when(clientes.findById(3L)).thenReturn(Optional.of(cliente));
        when(preVentas.save(any())).thenAnswer(i -> {
            PreVenta p = i.getArgument(0); if (p.getIdPreVenta() == null) p.setIdPreVenta(8L); return p;
        });
    }

    @Test void creaPendienteSinCodigoAunqueSeSoliciteOtroEstado() {
        PreVentaRequestDTO request = request(); request.setEstado("PAGADA");
        var result = service.crear(request);
        assertThat(result.getEstado()).isEqualTo("PENDIENTE");
        assertThat(result.getCodigoDisco()).isNull();
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
        return PreVenta.builder().idPreVenta(8L).cliente(cliente).descripcionSnap("Álbum largo")
            .codigoDisco("FUT-1").codigoDiscoNormalizado("fut-1").cantidad(2)
            .precio(new BigDecimal("1200")).estado("PENDIENTE").build();
    }
}
