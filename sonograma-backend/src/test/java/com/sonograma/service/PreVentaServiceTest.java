package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.entity.*;
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
    private PreVentaService service;
    private Cliente cliente;

    @BeforeEach void setup() {
        preVentas = mock(PreVentaRepository.class);
        ClienteRepository clientes = mock(ClienteRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        ventas = mock(VentaRepository.class);
        detalles = mock(DetalleVentaRepository.class);
        service = new PreVentaService(preVentas, clientes, discos, ventas, detalles,
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
