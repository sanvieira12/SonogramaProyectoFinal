package com.sonograma.service;

import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.Envio;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DireccionClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private EnvioRepository envioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private DiscoRepository discoRepository;
    @Mock private DireccionClienteRepository direccionClienteRepository;
    @Mock private DeudaRepository deudaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private PagoDeudaRepository pagoDeudaRepository;
    @Mock private DeudaService deudaService;
    @Mock private ClienteService clienteService;
    @Mock private DiscoQrCopyService discoQrCopyService;
    @Mock private DiscoEstadoService discoEstadoService;
    @Mock private ProfitCalculationService profitCalculationService;

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
                pagoDeudaRepository,
                clienteService,
                deudaService,
                new CostosVentaService(new ProfitCalculationService(
                        org.mockito.Mockito.mock(VentaRepository.class),
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoRepository.class),
                        org.mockito.Mockito.mock(com.sonograma.repository.PedidoItemRepository.class),
                        org.mockito.Mockito.mock(CatalogPricingService.class))),
                profitCalculationService,
                discoQrCopyService,
                discoEstadoService,
                new IngresoLibroCalculator()
        );
        lenient().doAnswer(invocation -> {
            Disco disco = invocation.getArgument(0);
            disco.setEstado(disco.getCantidadCopias() != null && disco.getCantidadCopias() > 0
                    ? EstadoDisco.DISPONIBLE : EstadoDisco.VENDIDO);
            return null;
        }).when(discoEstadoService).aplicar(any(Disco.class));
        lenient().when(profitCalculationService.netProfitForSale(any()))
                .thenReturn(new ProfitResult(BigDecimal.ZERO, ProfitStatus.ZERO, 0, java.util.List.of()));
    }

    @Test
    void registrarVentaConEnvioYPagoParcialNoIncluyeEnvioEnTotalNiDeudaYDescuentaStock() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "500", "3000", 1);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(discoQrCopyService.synchronize(disco)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(discoQrCopyService.countAvailableCopies(10L)).thenReturn(1L, 0L);
        when(discoQrCopyService.reserveCopies(disco, 1, null, null)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
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

        verify(deudaService).sincronizarVenta(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarVentaConProductosYDescuentoUsaSoloProductosYNoCreaDeudaSiSePagaCompleta() {
        Cliente cliente = cliente(1L);
        Disco discoA = disco(10L, "A", "Uno", "400", "1000", 1);
        Disco discoB = disco(11L, "B", "Dos", "600", "2000", 2);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(discoA));
        when(discoRepository.findById(11L)).thenReturn(Optional.of(discoB));
        when(discoQrCopyService.synchronize(discoA)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(discoQrCopyService.synchronize(discoB)).thenReturn(java.util.List.of(copy(11L, 2L, 1), copy(11L, 3L, 2)));
        when(discoQrCopyService.countAvailableCopies(10L)).thenReturn(1L, 0L);
        when(discoQrCopyService.countAvailableCopies(11L)).thenReturn(2L, 1L);
        when(discoQrCopyService.reserveCopies(discoA, 1, null, null)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(discoQrCopyService.reserveCopies(discoB, 1, null, null)).thenReturn(java.util.List.of(copy(11L, 2L, 1)));
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
    void registrarVentaRechazaPagoMayorAlTotalDeProductosAunquePayloadIncluyaEnvio() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "500", "3000", 1);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(discoQrCopyService.synchronize(disco)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(discoQrCopyService.countAvailableCopies(10L)).thenReturn(1L);

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

        assertThatThrownBy(() -> ventaService.registrarVenta(request))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("monto pagado no puede superar");
        verify(deudaRepository, never()).save(any(Deuda.class));
    }

    @Test
    void registrarVentaManualNoTocaStockYCreaDeudaSiPagoParcial() {
        Cliente cliente = cliente(1L);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(102L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaRequestDTO request = VentaRequestDTO.builder()
                .idCliente(1L)
                .detalles(java.util.List.of(
                        DetalleVentaDTO.builder()
                                .descripcion("Lote usado fuera de catálogo")
                                .cantidad(2)
                                .precioUnitario(new BigDecimal("750"))
                                .manualItem(true)
                                .build()
                ))
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new BigDecimal("1500"))
                .montoPagado(new BigDecimal("500"))
                .build();

        VentaResponseDTO response = ventaService.registrarVenta(request);

        assertThat(response.getTotalFinal()).isEqualByComparingTo("1500.00");
        assertThat(response.getMontoPagado()).isEqualByComparingTo("500.00");
        assertThat(response.getMontoDeuda()).isEqualByComparingTo("1000.00");
        assertThat(response.getDetalles()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getIdDisco()).isNull();
            assertThat(detalle.getManualItem()).isTrue();
            assertThat(detalle.getCantidad()).isEqualTo(2);
        });
        verify(discoRepository, never()).save(any(Disco.class));
        verify(deudaService).sincronizarVenta(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarVentaMixtaDescuentaSoloCatalogoSegunCantidad() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "500", "1000", 3);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(discoQrCopyService.synchronize(disco)).thenReturn(java.util.List.of(copy(10L, 1L, 1), copy(10L, 2L, 2), copy(10L, 3L, 3)));
        when(discoQrCopyService.countAvailableCopies(10L)).thenReturn(3L, 1L);
        when(discoQrCopyService.reserveCopies(disco, 2, null, null)).thenReturn(java.util.List.of(copy(10L, 1L, 1), copy(10L, 2L, 2)));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(103L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaRequestDTO request = VentaRequestDTO.builder()
                .idCliente(1L)
                .detalles(java.util.List.of(
                        DetalleVentaDTO.builder().idDisco(10L).cantidad(2).precioUnitario(new BigDecimal("1000")).build(),
                        DetalleVentaDTO.builder().descripcion("Disco feria").cantidad(1).precioUnitario(new BigDecimal("400")).manualItem(true).build()
                ))
                .canalVenta("LOCAL")
                .tipoEntrega("RETIRO")
                .total(new BigDecimal("2400"))
                .build();

        VentaResponseDTO response = ventaService.registrarVenta(request);

        assertThat(response.getTotalFinal()).isEqualByComparingTo("2400.00");
        assertThat(disco.getCantidadCopias()).isEqualTo(1);
        assertThat(disco.getEstado()).isEqualTo(EstadoDisco.DISPONIBLE);
        verify(discoRepository).save(disco);
        verify(deudaRepository, never()).save(any(Deuda.class));
    }

    @Test
    void obtenerLibroIncluyePagosDeDeudaComoIngresoSeparado() {
        Cliente cliente = cliente(1L);
        Venta venta = Venta.builder()
                .idVenta(200L)
                .cliente(cliente)
                .fechaVenta(LocalDateTime.of(2026, 6, 1, 10, 0))
                .numeroFactura("F-2026-001")
                .clienteNombreSnapshot("Cliente")
                .totalFinal(new BigDecimal("1000"))
                .precioVenta(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("400"))
                .montoDeuda(new BigDecimal("600"))
                .build();
        Deuda deuda = Deuda.builder()
                .idDeuda(300L)
                .venta(venta)
                .cliente(cliente)
                .montoTotal(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("700"))
                .montoPendiente(new BigDecimal("300"))
                .activa(true)
                .build();
        PagoDeuda pago = PagoDeuda.builder()
                .idPagoDeuda(400L)
                .deuda(deuda)
                .monto(new BigDecimal("300"))
                .fechaPago(LocalDate.of(2026, 6, 2))
                .createdAt(LocalDateTime.of(2026, 6, 3, 9, 0))
                .numeroRecibo("1258")
                .notas("Transferencia")
                .build();
        PagoDeuda pagoAnulado = PagoDeuda.builder()
                .idPagoDeuda(401L)
                .deuda(deuda)
                .monto(new BigDecimal("150"))
                .fechaPago(LocalDate.of(2026, 6, 4))
                .anulado(true)
                .numeroRecibo("1259")
                .build();

        when(ventaRepository.findAllByOrderByFechaVentaDesc()).thenReturn(java.util.List.of(venta));
        when(envioRepository.findByVentaIdVenta(200L)).thenReturn(Optional.empty());
        when(pagoDeudaRepository.findAll()).thenReturn(java.util.List.of(pago, pagoAnulado));

        var libro = ventaService.obtenerLibro(null, null, null, null);

        assertThat(libro).hasSize(2);
        assertThat(libro.get(0).getTipoMovimiento()).isEqualTo("PAGO_DEUDA");
        assertThat(libro.get(0).getDescripcionMovimiento()).isEqualTo("Pago de deuda");
        assertThat(libro.get(0).getMontoMovimiento()).isEqualByComparingTo("300");
        assertThat(libro.get(0).getFechaVenta()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay());
        assertThat(libro.get(0).getNumeroRecibo()).isEqualTo("1258");
        assertThat(libro.get(1).getTipoMovimiento()).isEqualTo("VENTA");
        assertThat(libro.get(1).getMontoMovimiento()).isEqualByComparingTo("400");
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

    private static DiscoQrCopy copy(Long discoId, Long id, int number) {
        return DiscoQrCopy.builder()
                .id(id)
                .idDisco(discoId)
                .copyNumber(number)
                .codigoQr("qr-" + id)
                .build();
    }
}
