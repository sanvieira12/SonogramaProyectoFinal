package com.sonograma.service;

import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Envio;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DireccionClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private EnvioRepository envioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private DiscoRepository discoRepository;
    @Mock private DireccionClienteRepository direccionClienteRepository;
    @Mock private DeudaRepository deudaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private ClienteService clienteService;

    private VentaService ventaService;

    @BeforeEach
    void setUp() {
        ventaService = new VentaService(
                ventaRepository,
                envioRepository,
                clienteRepository,
                discoRepository,
                direccionClienteRepository,
                deudaRepository,
                detalleVentaRepository,
                clienteService,
                new CostosVentaService()
        );
    }

    @Test
    void registrarVentaConEnvioYPagoParcialNoIncluyeEnvioEnTotalNiDeudaYDescuentaStock() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "500", "3000", 1);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(100L);
            return venta;
        });
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaRequestDTO request = VentaRequestDTO.builder()
                .idCliente(1L)
                .idDisco(10L)
                .canalVenta("LOCAL")
                .tipoEntrega("ENVIO")
                .departamento("Montevideo")
                .total(new BigDecimal("3250"))
                .precioVenta(new BigDecimal("3000"))
                .costoEnvio(new BigDecimal("250"))
                .montoPagado(new BigDecimal("2000"))
                .build();

        VentaResponseDTO response = ventaService.registrarVenta(request);

        assertThat(response.getTotalFinal()).isEqualByComparingTo("3000.00");
        assertThat(response.getCostoEnvio()).isEqualByComparingTo("250.00");
        assertThat(response.getMontoPagado()).isEqualByComparingTo("2000.00");
        assertThat(response.getMontoDeuda()).isEqualByComparingTo("1000.00");
        assertThat(response.getEstadoPago()).isEqualTo("PARCIAL");
        assertThat(disco.getCantidadCopias()).isZero();
        assertThat(disco.getEstado()).isEqualTo(EstadoDisco.VENDIDO);

        ArgumentCaptor<Deuda> deudaCaptor = ArgumentCaptor.forClass(Deuda.class);
        verify(deudaRepository).save(deudaCaptor.capture());
        Deuda deuda = deudaCaptor.getValue();
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("3000.00");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("2000.00");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("1000.00");
    }

    @Test
    void registrarVentaConProductosYDescuentoUsaSoloProductosYNoCreaDeudaSiSePagaCompleta() {
        Cliente cliente = cliente(1L);
        Disco discoA = disco(10L, "A", "Uno", "400", "1000", 1);
        Disco discoB = disco(11L, "B", "Dos", "600", "2000", 2);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(discoA));
        when(discoRepository.findById(11L)).thenReturn(Optional.of(discoB));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(101L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaRequestDTO request = VentaRequestDTO.builder()
                .idCliente(1L)
                .detalles(java.util.List.of(
                        DetalleVentaDTO.builder().idDisco(10L).precioUnitario(new BigDecimal("1000")).build(),
                        DetalleVentaDTO.builder().idDisco(11L).precioUnitario(new BigDecimal("2000")).build()
                ))
                .descuentoPorcentaje(new BigDecimal("10"))
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new BigDecimal("2700"))
                .build();

        VentaResponseDTO response = ventaService.registrarVenta(request);

        assertThat(response.getTotalFinal()).isEqualByComparingTo("2700.00");
        assertThat(response.getMontoPagado()).isEqualByComparingTo("2700.00");
        assertThat(response.getMontoDeuda()).isEqualByComparingTo("0.00");
        assertThat(response.getEstadoPago()).isEqualTo("PAGADO");
        assertThat(discoA.getCantidadCopias()).isZero();
        assertThat(discoB.getCantidadCopias()).isEqualTo(1);
        verify(deudaRepository, never()).save(any(Deuda.class));
    }

    @Test
    void registrarVentaNoGuardaPagoMayorAlTotalDeProductosAunquePayloadIncluyaEnvio() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "500", "3000", 1);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaRequestDTO request = VentaRequestDTO.builder()
                .idCliente(1L)
                .idDisco(10L)
                .canalVenta("LOCAL")
                .tipoEntrega("ENVIO")
                .departamento("Montevideo")
                .total(new BigDecimal("3250"))
                .precioVenta(new BigDecimal("3000"))
                .costoEnvio(new BigDecimal("250"))
                .montoPagado(new BigDecimal("3250"))
                .build();

        VentaResponseDTO response = ventaService.registrarVenta(request);

        assertThat(response.getTotalFinal()).isEqualByComparingTo("3000.00");
        assertThat(response.getMontoPagado()).isEqualByComparingTo("3000.00");
        assertThat(response.getMontoDeuda()).isEqualByComparingTo("0.00");
        verify(deudaRepository, never()).save(any(Deuda.class));
    }

    private static Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(id);
        cliente.setNombre("Cliente");
        cliente.setActivo(true);
        return cliente;
    }

    private static Disco disco(Long id, String artista, String album, String costo, String precio, int copias) {
        return Disco.builder()
                .idDisco(id)
                .artista(artista)
                .album(album)
                .costo(new BigDecimal(costo))
                .precioVenta(new BigDecimal(precio))
                .cantidadCopias(copias)
                .estado(EstadoDisco.DISPONIBLE)
                .build();
    }
}
