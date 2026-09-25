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
import com.sonograma.enums.ClasificacionItemVenta;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoVenta;
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
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private BusinessTime businessTime;

    @BeforeEach
    void setUp() {
        businessTime = new BusinessTime(Clock.fixed(Instant.parse("2026-09-14T13:00:00Z"), ZoneOffset.UTC));
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
                new IngresoLibroCalculator(deudaRepository),
                businessTime,
                new FinancialMovementPolicy()
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
                .dacBranchId("dac-661")
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
        assertThat(response.getFechaVenta()).isEqualTo(LocalDateTime.of(2026, 9, 14, 10, 0));
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
        discoA.setCondicion(CondicionDisco.NUEVO);
        discoB.setCondicion(CondicionDisco.USADO);
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
        assertThat(response.getDetalles()).extracting(com.sonograma.dto.DetalleVentaResponseDTO::getClasificacionItem)
                .containsExactly(ClasificacionItemVenta.NUEVO, ClasificacionItemVenta.USADO);
        verify(deudaRepository, never()).save(any(Deuda.class));
    }

    @Test
    void registrarVentaCapturaClasificacionCatalogoEnElDetalle() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(10L, "A", "Uno", "400", "1000", 1);
        disco.setCondicion(CondicionDisco.NUEVO);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(10L)).thenReturn(Optional.of(disco));
        when(discoQrCopyService.synchronize(disco)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(discoQrCopyService.countAvailableCopies(10L)).thenReturn(1L, 0L);
        when(discoQrCopyService.reserveCopies(disco, 1, null, null)).thenReturn(java.util.List.of(copy(10L, 1L, 1)));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(104L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaResponseDTO response = ventaService.registrarVenta(VentaRequestDTO.builder()
                .idCliente(1L)
                .detalles(java.util.List.of(DetalleVentaDTO.builder().idDisco(10L).precioUnitario(new BigDecimal("1000")).build()))
                .canalVenta("LOCAL").tipoEntrega("RETIRO").total(new BigDecimal("1000")).build());

        assertThat(response.getDetalles()).singleElement()
                .extracting(com.sonograma.dto.DetalleVentaResponseDTO::getClasificacionItem)
                .isEqualTo(ClasificacionItemVenta.NUEVO);
    }

    @Test
    void registrarVentaNoAdivinaClasificacionConsignacion() {
        Cliente cliente = cliente(1L);
        Disco disco = disco(12L, "A", "Consignado", "400", "1000", 1);
        disco.setCondicion(CondicionDisco.CONSIGNACION);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(discoRepository.findById(12L)).thenReturn(Optional.of(disco));
        when(discoQrCopyService.synchronize(disco)).thenReturn(java.util.List.of(copy(12L, 12L, 1)));
        when(discoQrCopyService.countAvailableCopies(12L)).thenReturn(1L, 0L);
        when(discoQrCopyService.reserveCopies(disco, 1, null, null)).thenReturn(java.util.List.of(copy(12L, 12L, 1)));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(107L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaResponseDTO response = ventaService.registrarVenta(VentaRequestDTO.builder().idCliente(1L)
                .detalles(java.util.List.of(DetalleVentaDTO.builder().idDisco(12L).precioUnitario(new BigDecimal("1000")).build()))
                .canalVenta("LOCAL").tipoEntrega("RETIRO").total(new BigDecimal("1000")).build());

        assertThat(response.getDetalles()).singleElement()
                .extracting(com.sonograma.dto.DetalleVentaResponseDTO::getClasificacionItem)
                .isNull();
    }

    @Test
    void registrarVentaManualExigeClasificacion() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente(1L)));
        VentaRequestDTO request = VentaRequestDTO.builder().idCliente(1L)
                .detalles(java.util.List.of(DetalleVentaDTO.builder().descripcion("Manual").precioUnitario(new BigDecimal("500")).build()))
                .canalVenta("LOCAL").tipoEntrega("RETIRO").total(new BigDecimal("500")).build();

        assertThatThrownBy(() -> ventaService.registrarVenta(request))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("nuevo o usado");
    }

    @Test
    void actualizarVentaPreservaSnapshotManualExistenteAunqueElPayloadNoLoRepita() {
        Cliente cliente = cliente(1L);
        DetalleVenta anterior = DetalleVenta.builder().idDetalle(77L).venta(null).disco(null)
                .precioUnitario(new BigDecimal("500")).cantidad(1).manualItem(true)
                .descripcionSnap("Manual histórico").clasificacionItem(ClasificacionItemVenta.USADO).build();
        Venta venta = Venta.builder().idVenta(106L).cliente(cliente).fechaVenta(LocalDateTime.of(2026, 9, 1, 10, 0))
                .estado(EstadoVenta.COMPLETADA).origen("VENTA").detalles(new java.util.ArrayList<>(java.util.List.of(anterior)))
                .build();
        anterior.setVenta(venta);
        when(ventaRepository.findById(106L)).thenReturn(Optional.of(venta));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ventaService.actualizarVenta(106L, VentaRequestDTO.builder().idCliente(1L).canalVenta("LOCAL")
                .tipoEntrega("RETIRO").total(new BigDecimal("650"))
                .detalles(java.util.List.of(DetalleVentaDTO.builder().idDetalle(77L).descripcion("Manual histórico")
                        .precioUnitario(new BigDecimal("650")).build())).build());

        verify(detalleVentaRepository).save(argThat(d -> d.getClasificacionItem() == ClasificacionItemVenta.USADO));
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
                .dacBranchId("dac-661")
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
                                .clasificacionItem(ClasificacionItemVenta.USADO)
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
            assertThat(detalle.getClasificacionItem()).isEqualTo(ClasificacionItemVenta.USADO);
        });
        verify(discoRepository, never()).save(any(Disco.class));
        verify(deudaService).sincronizarVenta(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarVentaManualCapturaClasificacionNueva() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente(1L)));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(105L);
            return venta;
        });
        when(detalleVentaRepository.save(any(DetalleVenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VentaResponseDTO response = ventaService.registrarVenta(VentaRequestDTO.builder().idCliente(1L)
                .detalles(java.util.List.of(DetalleVentaDTO.builder().descripcion("Manual nuevo")
                        .precioUnitario(new BigDecimal("500")).clasificacionItem(ClasificacionItemVenta.NUEVO).build()))
                .canalVenta("LOCAL").tipoEntrega("RETIRO").total(new BigDecimal("500")).build());

        assertThat(response.getDetalles()).singleElement()
                .extracting(com.sonograma.dto.DetalleVentaResponseDTO::getClasificacionItem)
                .isEqualTo(ClasificacionItemVenta.NUEVO);
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
                        DetalleVentaDTO.builder().descripcion("Disco feria").cantidad(1).precioUnitario(new BigDecimal("400")).manualItem(true).clasificacionItem(ClasificacionItemVenta.USADO).build()
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
    void obtenerLibroIncluyePagosDeDeudaComoIngresoSeparado() throws Exception {
        Cliente cliente = cliente(1L);
        Venta venta = Venta.builder()
                .idVenta(200L)
                .cliente(cliente)
                .fechaVenta(LocalDateTime.of(2026, 6, 1, 10, 0))
                .numeroFactura("F-2026-001")
                .clienteNombreSnapshot("Cliente")
                .totalFinal(new BigDecimal("1000"))
                .precioVenta(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("700"))
                .montoDeuda(new BigDecimal("300"))
                .build();
        Deuda deuda = Deuda.builder()
                .idDeuda(300L)
                .venta(venta)
                .cliente(cliente)
                .montoTotal(new BigDecimal("1000"))
                .montoPagadoInicial(new BigDecimal("400"))
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
        when(deudaRepository.findByVentaIdVenta(200L)).thenReturn(Optional.of(deuda));

        var libro = ventaService.obtenerLibro(null, null, null, null);

        assertThat(libro).hasSize(2);
        assertThat(libro.get(0).getTipoMovimiento()).isEqualTo("PAGO_DEUDA");
        assertThat(libro.get(0).getDescripcionMovimiento()).isEqualTo("Pago de deuda");
        assertThat(libro.get(0).getMontoMovimiento()).isEqualByComparingTo("300");
        assertThat(libro.get(0).getFechaVenta()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay());
        assertThat(libro.get(0).getNumeroRecibo()).isEqualTo("1258");
        assertThat(libro.get(1).getTipoMovimiento()).isEqualTo("VENTA");
        assertThat(libro.get(1).getMontoMovimiento()).isEqualByComparingTo("400");

        byte[] export = new ExcelExportService(profitCalculationService).exportarLibroMovimientos(libro);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheetAt(0).getRow(4).getCell(8).getNumericCellValue()).isEqualTo(700.00);
        }
    }

    @Test
    void obtenerLibroUsaPagoInicialEnVentaParcialSinCobrosPosteriores() {
        Cliente cliente = cliente(3L);
        Venta venta = Venta.builder()
                .idVenta(202L)
                .cliente(cliente)
                .fechaVenta(LocalDateTime.of(2026, 6, 1, 10, 0))
                .totalFinal(new BigDecimal("1000"))
                .precioVenta(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("400"))
                .montoDeuda(new BigDecimal("600"))
                .build();
        Deuda deuda = Deuda.builder()
                .idDeuda(302L)
                .venta(venta)
                .cliente(cliente)
                .montoTotal(new BigDecimal("1000"))
                .montoPagadoInicial(new BigDecimal("400"))
                .montoPagado(new BigDecimal("400"))
                .montoPendiente(new BigDecimal("600"))
                .activa(true)
                .build();

        when(ventaRepository.findAllByOrderByFechaVentaDesc()).thenReturn(java.util.List.of(venta));
        when(envioRepository.findByVentaIdVenta(202L)).thenReturn(Optional.empty());
        when(pagoDeudaRepository.findAll()).thenReturn(java.util.List.of());
        when(deudaRepository.findByVentaIdVenta(202L)).thenReturn(Optional.of(deuda));

        var libro = ventaService.obtenerLibro(null, null, null, null);

        assertThat(libro).hasSize(1);
        assertThat(libro.get(0).getTipoMovimiento()).isEqualTo("VENTA");
        assertThat(libro.get(0).getMontoMovimiento()).isEqualByComparingTo("400");
    }

    @Test
    void obtenerLibroManualDebtSoloIncluyePagosSinVentaSintetica() {
        Cliente cliente = cliente(4L);
        Deuda deuda = Deuda.builder().idDeuda(303L).cliente(cliente).build();
        PagoDeuda pago = PagoDeuda.builder()
                .idPagoDeuda(403L)
                .deuda(deuda)
                .monto(new BigDecimal("300"))
                .fechaPago(LocalDate.of(2026, 6, 2))
                .build();

        when(ventaRepository.findAllByOrderByFechaVentaDesc()).thenReturn(java.util.List.of());
        when(pagoDeudaRepository.findAll()).thenReturn(java.util.List.of(pago));

        var libro = ventaService.obtenerLibro(null, null, null, null);

        assertThat(libro).hasSize(1);
        assertThat(libro.get(0).getIdVenta()).isNull();
        assertThat(libro.get(0).getTipoMovimiento()).isEqualTo("PAGO_DEUDA");
        assertThat(libro.get(0).getMontoMovimiento()).isEqualByComparingTo("300");
    }

    @Test
    void estadisticasPorMesUsaPagoInicialYNoDuplicaPagoPosterior() {
        Cliente cliente = cliente(5L);
        Venta venta = Venta.builder()
                .idVenta(204L)
                .cliente(cliente)
                .fechaVenta(LocalDateTime.of(2026, 6, 1, 10, 0))
                .totalFinal(new BigDecimal("1000"))
                .precioVenta(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("1000"))
                .build();
        Deuda deuda = Deuda.builder()
                .idDeuda(304L)
                .venta(venta)
                .montoPagadoInicial(new BigDecimal("400"))
                .build();
        PagoDeuda pago = PagoDeuda.builder()
                .idPagoDeuda(404L)
                .deuda(deuda)
                .monto(new BigDecimal("600"))
                .fechaPago(LocalDate.of(2026, 6, 2))
                .build();

        when(ventaRepository.findAll()).thenReturn(java.util.List.of(venta));
        when(pagoDeudaRepository.findAll()).thenReturn(java.util.List.of(pago));
        when(deudaRepository.findByVentaIdVenta(204L)).thenReturn(Optional.of(deuda));

        var estadisticas = ventaService.obtenerEstadisticasPorMes();

        assertThat(estadisticas).hasSize(1);
        assertThat(estadisticas.get(0).getTotalMonto()).isEqualByComparingTo("1000");
    }

    @Test
    void obtenerLibroIncluyePagoDeDeudaAunqueLaDeudaYaEsteSaldada() {
        Cliente cliente = cliente(2L);
        Venta venta = Venta.builder()
                .idVenta(201L)
                .cliente(cliente)
                .fechaVenta(LocalDateTime.of(2026, 6, 1, 10, 0))
                .totalFinal(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("1000"))
                .montoDeuda(BigDecimal.ZERO)
                .build();
        Deuda deuda = Deuda.builder()
                .idDeuda(301L)
                .venta(venta)
                .cliente(cliente)
                .montoTotal(new BigDecimal("1000"))
                .montoPagado(new BigDecimal("1000"))
                .montoPendiente(BigDecimal.ZERO)
                .estadoPago(com.sonograma.enums.EstadoPago.PAGADO)
                .activa(false)
                .build();
        PagoDeuda pago = PagoDeuda.builder()
                .idPagoDeuda(402L)
                .deuda(deuda)
                .monto(new BigDecimal("600"))
                .fechaPago(LocalDate.of(2026, 6, 2))
                .build();

        when(ventaRepository.findAllByOrderByFechaVentaDesc()).thenReturn(java.util.List.of(venta));
        when(envioRepository.findByVentaIdVenta(201L)).thenReturn(Optional.empty());
        when(pagoDeudaRepository.findAll()).thenReturn(java.util.List.of(pago));

        var libro = ventaService.obtenerLibro(null, null, null, null);

        assertThat(libro).anySatisfy(row -> {
            assertThat(row.getTipoMovimiento()).isEqualTo("PAGO_DEUDA");
            assertThat(row.getMontoMovimiento()).isEqualByComparingTo("600");
        });
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
