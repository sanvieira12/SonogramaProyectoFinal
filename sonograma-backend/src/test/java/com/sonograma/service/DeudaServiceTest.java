package com.sonograma.service;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.dto.PagoDeudaDTO;
import com.sonograma.dto.PagoDeudaUpdateRequest;
import com.sonograma.dto.DeudaRequestDTO;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import com.sonograma.repository.DiscoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    @Mock com.sonograma.repository.DetalleVentaRepository detalleVentaRepository;
    @Mock PagoDeudaRepository pagoDeudaRepository;
    @Mock VentaRepository ventaRepository;
    @Mock DiscoRepository discoRepository;
    @Mock DiscoQrCopyService discoQrCopyService;
    @Mock DiscoEstadoService discoEstadoService;
    private DeudaService service;
    private BusinessTime businessTime;

    @BeforeEach
    void setUp() {
        businessTime = new BusinessTime(Clock.fixed(Instant.parse("2026-09-15T01:30:00Z"), ZoneOffset.UTC));
        service = new DeudaService(
                deudaRepository, clienteRepository, detalleVentaRepository, pagoDeudaRepository, ventaRepository,
                discoRepository, discoQrCopyService, discoEstadoService,
                businessTime, new FinancialMovementPolicy());
    }

    @Test
    void eliminaDeudaDeVentaRestauraSoloLasCopiasExactasYEliminaSusPagos() {
        Disco disco = Disco.builder().idDisco(10L).cantidadCopias(0).estado(EstadoDisco.SIN_STOCK).build();
        DetalleVenta detalle = DetalleVenta.builder().idDetalle(20L).disco(disco).cantidad(2)
                .copyIdsSnapshot("101,102").build();
        Venta venta = Venta.builder().idVenta(30L).estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PARCIAL).montoDeuda(new BigDecimal("500"))
                .detalles(new ArrayList<>(List.of(detalle))).build();
        Deuda deuda = Deuda.builder().idDeuda(40L).activa(true).venta(venta)
                .montoTotal(new BigDecimal("2000")).montoPendiente(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(50L).deuda(deuda)
                .monto(new BigDecimal("1500")).build();
        PagoDeuda pagoAnulado = PagoDeuda.builder().idPagoDeuda(51L).deuda(deuda)
                .monto(new BigDecimal("100")).anulado(true).build();

        when(deudaRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findAllByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(40L))
                .thenReturn(List.of(pago, pagoAnulado));
        when(discoQrCopyService.hasCopyInventory(10L)).thenReturn(true);

        service.eliminar(40L);

        verify(discoQrCopyService).restoreCopiesForDebt(disco, "101,102");
        verify(discoEstadoService).aplicar(disco);
        verify(pagoDeudaRepository).deleteAll(List.of(pago, pagoAnulado));
        verify(pagoDeudaRepository).flush();
        verify(ventaRepository).save(venta);
        verify(deudaRepository).delete(deuda);
        verify(deudaRepository).flush();
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.CANCELADA);
        assertThat(venta.getMontoDeuda()).isZero();
    }

    @Test
    void noConfundeDosDetallesDeLaVentaOrigenConOtraVenta() {
        Disco primerDisco = Disco.builder().idDisco(10L).cantidadCopias(0).estado(EstadoDisco.SIN_STOCK).build();
        Disco segundoDisco = Disco.builder().idDisco(11L).cantidadCopias(0).estado(EstadoDisco.SIN_STOCK).build();
        Venta venta = Venta.builder().idVenta(30L).estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PARCIAL).montoDeuda(new BigDecimal("500"))
                .build();
        DetalleVenta primerDetalle = DetalleVenta.builder().idDetalle(20L).venta(venta).disco(primerDisco)
                .cantidad(1).copyIdsSnapshot("101").build();
        DetalleVenta segundoDetalle = DetalleVenta.builder().idDetalle(21L).venta(venta).disco(segundoDisco)
                .cantidad(1).copyIdsSnapshot("102").build();
        venta.setDetalles(new ArrayList<>(List.of(primerDetalle, segundoDetalle)));
        Deuda deuda = Deuda.builder().idDeuda(40L).activa(true).venta(venta)
                .montoTotal(new BigDecimal("2000")).montoPendiente(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();

        when(deudaRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findAllByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(40L))
                .thenReturn(List.of());
        when(discoQrCopyService.hasCopyInventory(10L)).thenReturn(true);
        when(discoQrCopyService.hasCopyInventory(11L)).thenReturn(true);
        when(detalleVentaRepository.findAllWithCopyIdsFromActiveSales(EstadoVenta.CANCELADA))
                .thenReturn(List.of(primerDetalle, segundoDetalle));

        service.eliminar(40L);

        verify(discoQrCopyService).restoreCopiesForDebt(primerDisco, "101");
        verify(discoQrCopyService).restoreCopiesForDebt(segundoDisco, "102");
        verify(deudaRepository).delete(deuda);
    }

    @Test
    void bloqueaSoloCuandoLaCopiaTambienPerteneceAUnaVentaDistinta() {
        Disco disco = Disco.builder().idDisco(10L).cantidadCopias(0).estado(EstadoDisco.SIN_STOCK).build();
        Venta ventaOrigen = Venta.builder().idVenta(30L).estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PARCIAL).montoDeuda(new BigDecimal("500")).build();
        DetalleVenta detalleOrigen = DetalleVenta.builder().idDetalle(20L).venta(ventaOrigen).disco(disco)
                .cantidad(1).copyIdsSnapshot("101").build();
        ventaOrigen.setDetalles(new ArrayList<>(List.of(detalleOrigen)));

        Venta otraVenta = Venta.builder().idVenta(31L).estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PAGADO).montoDeuda(BigDecimal.ZERO).build();
        DetalleVenta detalleOtraVenta = DetalleVenta.builder().idDetalle(21L).venta(otraVenta).disco(disco)
                .cantidad(1).copyIdsSnapshot("101").build();
        Deuda deuda = Deuda.builder().idDeuda(40L).activa(true).venta(ventaOrigen)
                .montoTotal(new BigDecimal("2000")).montoPendiente(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();

        when(deudaRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(deuda));
        when(discoQrCopyService.hasCopyInventory(10L)).thenReturn(true);
        when(detalleVentaRepository.findAllWithCopyIdsFromActiveSales(EstadoVenta.CANCELADA))
                .thenReturn(List.of(detalleOrigen, detalleOtraVenta));

        assertThatThrownBy(() -> service.eliminar(40L))
                .isInstanceOf(com.sonograma.exception.ConflictoNegocioException.class)
                .hasMessage("No se puede eliminar la deuda porque uno de los discos pertenece a otra venta.");

        verify(deudaRepository, org.mockito.Mockito.never()).delete(any());
        org.mockito.Mockito.verifyNoInteractions(pagoDeudaRepository, ventaRepository);
    }

    @Test
    void eliminaDeudaManualSinDiscosSinTocarOtrosPagos() {
        Deuda deuda = Deuda.builder().idDeuda(41L).activa(true).montoTotal(new BigDecimal("100"))
                .montoPendiente(new BigDecimal("100")).estadoPago(EstadoPago.PENDIENTE).build();
        when(deudaRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findAllByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(41L))
                .thenReturn(List.of());

        service.eliminar(41L);

        verify(deudaRepository).delete(deuda);
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).deleteAll(any());
        org.mockito.Mockito.verifyNoInteractions(ventaRepository, discoRepository, discoQrCopyService, discoEstadoService);
    }

    @Test
    void noEliminaUnaVentaCompletamentePaga() {
        Venta venta = Venta.builder().estado(EstadoVenta.COMPLETADA)
                .estadoPago(EstadoPago.PAGADO).montoDeuda(BigDecimal.ZERO).build();
        Deuda deuda = Deuda.builder().idDeuda(42L).activa(true).venta(venta)
                .montoPendiente(BigDecimal.ZERO).build();
        when(deudaRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(deuda));

        assertThatThrownBy(() -> service.eliminar(42L))
                .isInstanceOf(com.sonograma.exception.ConflictoNegocioException.class);
        verify(deudaRepository, org.mockito.Mockito.never()).delete(any());
        org.mockito.Mockito.verifyNoInteractions(discoQrCopyService, pagoDeudaRepository, ventaRepository);
    }

    @Test
    void eliminarDeudaInexistenteDevuelveRecursoNoEncontrado() {
        when(deudaRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(404L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(deudaRepository, org.mockito.Mockito.never()).delete(any());
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
        assertThat(pagos).extracting(PagoDeuda::getMonto)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("1500"));
        assertThat(pagos).extracting(PagoDeuda::getFechaPago)
                .containsExactly(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));
        assertThat(pagos).allSatisfy(pago -> assertThat(pago.getDeuda()).isSameAs(deuda));
        assertThat(completoConBoleta.getPagos()).extracting(PagoDeudaDTO::getNumeroRecibo)
                .containsExactly("1258", null, "1320");

        service.registrarPago(1L, new BigDecimal("1500"), null, "1320", "payment-3");

        assertThat(pagos).hasSize(3);
    }

    @Test
    void actualizarNoPermiteCambiarMontoPagadoNiCrearPago() {
        Cliente cliente = cliente(2L, "Cliente");
        Deuda deuda = Deuda.builder().idDeuda(2L).cliente(cliente).activa(true)
                .montoTotal(new BigDecimal("2000")).montoPagadoInicial(new BigDecimal("500"))
                .montoPagado(new BigDecimal("500")).montoPendiente(new BigDecimal("1500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        when(deudaRepository.findById(2L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(2L))
                .thenReturn(List.of());

        DeudaRequestDTO request = DeudaRequestDTO.builder()
                .idCliente(2L)
                .montoTotal(new BigDecimal("2000"))
                .montoPagado(new BigDecimal("2000"))
                .estadoPago("PAGADO")
                .build();

        assertThatThrownBy(() -> service.actualizar(2L, request))
                .isInstanceOf(com.sonograma.exception.NegocioException.class)
                .hasMessageContaining("Registrar pago");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("500");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("1500");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).save(any(PagoDeuda.class));
        verify(deudaRepository, org.mockito.Mockito.never()).save(any(Deuda.class));
    }

    @Test
    void actualizarNoPuedeMarcarPagadaUnaDeudaSoloCambiandoElEstado() {
        Cliente cliente = cliente(3L, "Cliente");
        Deuda deuda = Deuda.builder().idDeuda(3L).cliente(cliente).activa(true)
                .montoTotal(new BigDecimal("2000")).montoPagadoInicial(new BigDecimal("500"))
                .montoPagado(new BigDecimal("500")).montoPendiente(new BigDecimal("1500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        when(deudaRepository.findById(3L)).thenReturn(Optional.of(deuda));
        when(clienteRepository.findById(3L)).thenReturn(Optional.of(cliente));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(3L))
                .thenReturn(List.of());
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeudaRequestDTO request = DeudaRequestDTO.builder()
                .idCliente(3L)
                .montoTotal(new BigDecimal("2000"))
                .estadoPago("PAGADO")
                .build();

        var response = service.actualizar(3L, request);

        assertThat(response.getMontoPagado()).isEqualByComparingTo("500");
        assertThat(response.getMontoPendiente()).isEqualByComparingTo("1500");
        assertThat(response.getEstadoPago()).isEqualTo("PARCIAL");
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).save(any(PagoDeuda.class));
    }

    @Test
    void deudaVinculadaRechazaUnTotalDistintoAlTotalDeLaVentaSinMutar() {
        Venta venta = Venta.builder().idVenta(25L)
                .total(new BigDecimal("1900")).totalFinal(new BigDecimal("1900"))
                .montoPagado(new BigDecimal("1500")).montoDeuda(new BigDecimal("400"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(30L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("1900")).montoPagadoInicial(new BigDecimal("1500"))
                .montoPagado(new BigDecimal("1500")).montoPendiente(new BigDecimal("400"))
                .estadoPago(EstadoPago.PARCIAL).build();
        when(deudaRepository.findById(30L)).thenReturn(Optional.of(deuda));

        DeudaRequestDTO request = DeudaRequestDTO.builder()
                .montoTotal(new BigDecimal("2300")).build();

        assertThatThrownBy(() -> service.actualizar(30L, request))
                .isInstanceOf(NegocioException.class)
                .hasMessage("El total de una deuda vinculada debe coincidir con el total de la venta; editá la venta para cambiarlo");
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("1900");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("1900");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("400");
        verify(deudaRepository, org.mockito.Mockito.never()).save(any(Deuda.class));
    }

    @Test
    void deudaManualPuedeCambiarMontoTotalYRecalculaPendiente() {
        Cliente cliente = cliente(4L, "Cliente");
        Deuda deuda = Deuda.builder().idDeuda(4L).cliente(cliente).activa(true)
                .montoTotal(new BigDecimal("1000")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(BigDecimal.ZERO).montoPendiente(new BigDecimal("1000"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        when(deudaRepository.findById(4L)).thenReturn(Optional.of(deuda));
        when(clienteRepository.findById(4L)).thenReturn(Optional.of(cliente));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(4L))
                .thenReturn(List.of());
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.actualizar(4L, DeudaRequestDTO.builder()
                .idCliente(4L).montoTotal(new BigDecimal("1500")).build());

        assertThat(response.getMontoTotal()).isEqualByComparingTo("1500");
        assertThat(response.getMontoPagado()).isEqualByComparingTo("0");
        assertThat(response.getMontoPendiente()).isEqualByComparingTo("1500");
        assertThat(response.getEstadoPago()).isEqualTo("PENDIENTE");
        verify(deudaRepository).save(deuda);
    }

    @Test
    void pagoDeDeudaVinculadaSincronizaLosCachesDeLaVenta() {
        Venta venta = Venta.builder().idVenta(200L).totalFinal(new BigDecimal("1900"))
                .montoPagado(new BigDecimal("1500")).montoDeuda(new BigDecimal("400"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(200L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("1900")).montoPagadoInicial(new BigDecimal("1500"))
                .montoPagado(new BigDecimal("1500")).montoPendiente(new BigDecimal("400"))
                .estadoPago(EstadoPago.PARCIAL).build();
        List<PagoDeuda> pagos = new ArrayList<>();
        when(deudaRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(200L))
                .thenAnswer(invocation -> List.copyOf(pagos));
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> {
            PagoDeuda pago = invocation.getArgument(0);
            pago.setIdPagoDeuda(1L);
            pagos.add(pago);
            return pago;
        });
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registrarPago(200L, new BigDecimal("250"), null, null, "linked-payment");

        assertThat(response.getMontoPagado()).isEqualByComparingTo("1750");
        assertThat(response.getMontoPendiente()).isEqualByComparingTo("150");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("1750");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("150");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
    }

    @Test
    void pagoYReversionPreservanTotalesDeDeudaVinculadaHistoricamenteDivergente() {
        Venta venta = Venta.builder().idVenta(34L).totalFinal(new BigDecimal("1390"))
                .montoPagado(BigDecimal.ZERO).montoDeuda(new BigDecimal("790"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        Deuda deuda = Deuda.builder().idDeuda(53L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("790")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(BigDecimal.ZERO).montoPendiente(new BigDecimal("790"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        List<PagoDeuda> pagos = new ArrayList<>();
        when(deudaRepository.findByIdForUpdate(53L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaAndIdempotencyKey(53L, "historical-divergent-payment"))
                .thenReturn(Optional.empty());
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(53L))
                .thenAnswer(invocation -> List.copyOf(pagos));
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> {
            PagoDeuda pago = invocation.getArgument(0);
            pago.setIdPagoDeuda(5300L);
            if (!pagos.contains(pago)) {
                pagos.add(pago);
            }
            return pago;
        });
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registrarPago(53L, new BigDecimal("300"), null, null,
                "historical-divergent-payment");

        assertThat(response.getMontoPagado()).isEqualByComparingTo("300");
        assertThat(response.getMontoPendiente()).isEqualByComparingTo("490");
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("790");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("300");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("490");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("1390");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("300");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("490");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);

        PagoDeuda pago = pagos.get(0);
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(5300L)).thenReturn(Optional.of(pago));

        service.eliminarPago(5300L, "historical-operator");

        assertThat(pago.getAnulado()).isTrue();
        assertThat(pago.getMonto()).isEqualByComparingTo("300");
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("790");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("0");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("790");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("1390");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("0");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("790");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        verify(pagoDeudaRepository, org.mockito.Mockito.times(2)).save(pago);
        verify(deudaRepository, org.mockito.Mockito.atLeastOnce()).save(deuda);
        org.mockito.Mockito.verify(pagoDeudaRepository, org.mockito.Mockito.never()).delete(any(PagoDeuda.class));
    }

    @Test
    void editaPagoExistenteReduciendoMontoYRecalculaSoloLosCaches() {
        Venta venta = Venta.builder().idVenta(40L).totalFinal(new BigDecimal("2000"))
                .montoPagado(new BigDecimal("1500")).montoDeuda(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(40L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("2000")).montoPagadoInicial(new BigDecimal("500"))
                .montoPagado(new BigDecimal("1500")).montoPendiente(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(400L).deuda(deuda)
                .monto(new BigDecimal("1000")).fechaPago(LocalDate.of(2026, 7, 10))
                .numeroRecibo("B-1").notas("Original").idempotencyKey("stable-key").build();

        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(400L)).thenReturn(Optional.of(pago));
        when(deudaRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(40L))
                .thenReturn(List.of(pago));
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarPago(400L, PagoDeudaUpdateRequest.builder()
                .monto(new BigDecimal("700"))
                .fechaPago(LocalDate.of(2026, 7, 11))
                .numeroRecibo("B-2")
                .notas("Corregido")
                .build());

        assertThat(pago.getIdPagoDeuda()).isEqualTo(400L);
        assertThat(pago.getMonto()).isEqualByComparingTo("700");
        assertThat(pago.getFechaPago()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(pago.getNumeroRecibo()).isEqualTo("B-2");
        assertThat(pago.getNotas()).isEqualTo("Corregido");
        assertThat(pago.getIdempotencyKey()).isEqualTo("stable-key");
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("2000");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("1200");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("800");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("2000");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("1200");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("800");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
        verify(pagoDeudaRepository).save(pago);
        verify(deudaRepository).save(deuda);
    }

    @Test
    void editaPagoExistenteAumentandoMontoSinCrearOtroMovimiento() {
        Venta venta = Venta.builder().idVenta(41L).totalFinal(new BigDecimal("2000"))
                .montoPagado(new BigDecimal("1200")).montoDeuda(new BigDecimal("800"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(41L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("2000")).montoPagadoInicial(new BigDecimal("500"))
                .montoPagado(new BigDecimal("1200")).montoPendiente(new BigDecimal("800"))
                .estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(410L).deuda(deuda)
                .monto(new BigDecimal("700")).fechaPago(LocalDate.of(2026, 7, 11)).build();

        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(410L)).thenReturn(Optional.of(pago));
        when(deudaRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(41L))
                .thenReturn(List.of(pago));
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarPago(410L, PagoDeudaUpdateRequest.builder()
                .monto(new BigDecimal("1000"))
                .fechaPago(LocalDate.of(2026, 7, 12))
                .build());

        assertThat(pago.getMonto()).isEqualByComparingTo("1000");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("1500");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("500");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("2000");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("1500");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("500");
        verify(pagoDeudaRepository).save(pago);
    }

    @Test
    void rechazaEdicionDePagoExcesivaSinMutacionParcial() {
        Venta venta = Venta.builder().idVenta(42L).totalFinal(new BigDecimal("2000"))
                .montoPagado(new BigDecimal("1500")).montoDeuda(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(42L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("2000")).montoPagadoInicial(new BigDecimal("500"))
                .montoPagado(new BigDecimal("1500")).montoPendiente(new BigDecimal("500"))
                .estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(420L).deuda(deuda)
                .monto(new BigDecimal("1000")).fechaPago(LocalDate.of(2026, 7, 10)).build();

        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(420L)).thenReturn(Optional.of(pago));
        when(deudaRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(42L))
                .thenReturn(List.of(pago));

        assertThatThrownBy(() -> service.actualizarPago(420L, PagoDeudaUpdateRequest.builder()
                .monto(new BigDecimal("1600"))
                .fechaPago(LocalDate.of(2026, 7, 12))
                .numeroRecibo("NO-GUARDAR")
                .build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("El monto actualizado excede la deuda pendiente");

        assertThat(pago.getMonto()).isEqualByComparingTo("1000");
        assertThat(pago.getFechaPago()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(pago.getNumeroRecibo()).isNull();
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("1500");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("500");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("2000");
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).save(any(PagoDeuda.class));
        verify(deudaRepository, org.mockito.Mockito.never()).save(any(Deuda.class));
    }

    @Test
    void rechazaEditarPagoAnuladoSinMutarLaFila() {
        Deuda deuda = Deuda.builder().idDeuda(43L).activa(true)
                .montoTotal(new BigDecimal("1000")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(BigDecimal.ZERO).montoPendiente(new BigDecimal("1000"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(430L).deuda(deuda)
                .monto(new BigDecimal("300")).fechaPago(LocalDate.of(2026, 7, 10))
                .anulado(true).build();
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(430L)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> service.actualizarPago(430L, PagoDeudaUpdateRequest.builder()
                .monto(new BigDecimal("200")).fechaPago(LocalDate.of(2026, 7, 12)).build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("El pago de deuda ya fue anulado");

        assertThat(pago.getMonto()).isEqualByComparingTo("300");
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).save(any(PagoDeuda.class));
        org.mockito.Mockito.verifyNoInteractions(deudaRepository);
    }

    @Test
    void editaPagoDeDeudaHistoricamenteDivergenteSinNormalizarTotales() {
        Venta venta = Venta.builder().idVenta(34L).totalFinal(new BigDecimal("1390"))
                .montoPagado(new BigDecimal("300")).montoDeuda(new BigDecimal("490"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(53L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("790")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(new BigDecimal("300")).montoPendiente(new BigDecimal("490"))
                .estadoPago(EstadoPago.PARCIAL).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(5300L).deuda(deuda)
                .monto(new BigDecimal("300")).fechaPago(LocalDate.of(2026, 7, 10)).build();
        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(5300L)).thenReturn(Optional.of(pago));
        when(deudaRepository.findByIdForUpdate(53L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(53L))
                .thenReturn(List.of(pago));
        when(pagoDeudaRepository.save(any(PagoDeuda.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarPago(5300L, PagoDeudaUpdateRequest.builder()
                .monto(new BigDecimal("200")).fechaPago(LocalDate.of(2026, 7, 11)).build());

        assertThat(pago.getMonto()).isEqualByComparingTo("200");
        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("790");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("200");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("590");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("1390");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("200");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("590");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
    }

    @Test
    void edicionDirectaRechazaCambiarElTotalDeUnaDeudaHistoricamenteDivergente() {
        Venta venta = Venta.builder().idVenta(34L).totalFinal(new BigDecimal("1390"))
                .montoPagado(BigDecimal.ZERO).montoDeuda(new BigDecimal("790"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        Deuda deuda = Deuda.builder().idDeuda(53L).venta(venta).activa(true)
                .montoTotal(new BigDecimal("790")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(BigDecimal.ZERO).montoPendiente(new BigDecimal("790"))
                .estadoPago(EstadoPago.PENDIENTE).build();
        when(deudaRepository.findById(53L)).thenReturn(Optional.of(deuda));

        assertThatThrownBy(() -> service.actualizar(53L, DeudaRequestDTO.builder()
                .montoTotal(new BigDecimal("1000")).build()))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La deuda vinculada no coincide con el total de la venta; editá la venta para cambiarlo");

        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("790");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("1390");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("790");
    }

    @Test
    void edicionDeVentaSincronizaElNuevoTotalConLaDeudaVinculada() {
        Cliente cliente = cliente(8L, "Cliente");
        Venta venta = Venta.builder().idVenta(8L).totalFinal(new BigDecimal("2300"))
                .montoPagado(new BigDecimal("1500")).montoDeuda(new BigDecimal("800"))
                .estadoPago(EstadoPago.PARCIAL).build();
        Deuda deuda = Deuda.builder().idDeuda(8L).venta(venta).cliente(cliente).activa(true)
                .montoTotal(new BigDecimal("1900")).montoPagadoInicial(new BigDecimal("1500"))
                .montoPagado(new BigDecimal("1500")).montoPendiente(new BigDecimal("400"))
                .estadoPago(EstadoPago.PARCIAL).build();
        when(clienteRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(cliente));
        when(deudaRepository.findByVentaIdVenta(8L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(8L))
                .thenReturn(List.of());
        when(deudaRepository.save(any(Deuda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sincronizarVenta(venta, cliente, new BigDecimal("2300"), new BigDecimal("1500"),
                new BigDecimal("800"), EstadoPago.PARCIAL, LocalDateTime.of(2026, 9, 14, 12, 0));

        assertThat(deuda.getMontoTotal()).isEqualByComparingTo("2300");
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("1500");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("800");
        assertThat(venta.getTotalFinal()).isEqualByComparingTo("2300");
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("1500");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("800");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
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

        LocalDateTime createdAt = eliminado.getCreatedAt();
        service.eliminarPago(10L, "operador-1");

        assertThat(eliminado.getAnulado()).isTrue();
        assertThat(eliminado.getFechaAnulacion()).isEqualTo(LocalDateTime.of(2026, 9, 14, 22, 30));
        assertThat(eliminado.getAnuladoPor()).isEqualTo("operador-1");
        assertThat(eliminado.getMonto()).isEqualByComparingTo("5000");
        assertThat(eliminado.getFechaPago()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(eliminado.getCreatedAt()).isEqualTo(createdAt);
        assertThat(deuda.getMontoPagado()).isEqualByComparingTo("2000");
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("6000");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PARCIAL);
        assertThat(deuda.getFechaUltimoPago()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(venta.getMontoPagado()).isEqualByComparingTo("2000");
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("6000");
        verify(pagoDeudaRepository).save(eliminado);
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).delete(any(PagoDeuda.class));
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

        verify(pagoDeudaRepository).save(pago);
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).delete(any(PagoDeuda.class));
        assertThat(deuda.getMontoPagado()).isZero();
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("3000");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(venta.getMontoPagado()).isZero();
        assertThat(venta.getMontoDeuda()).isEqualByComparingTo("3000");
        assertThat(venta.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void eliminarPagoUsaLaDeudaRelacionadaAunqueEsteInactiva() {
        Deuda deuda = Deuda.builder().idDeuda(66L).activa(false)
                .montoTotal(new BigDecimal("1370")).montoPagadoInicial(BigDecimal.ZERO)
                .montoPagado(new BigDecimal("1370")).montoPendiente(BigDecimal.ZERO)
                .estadoPago(EstadoPago.PAGADO).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(25L).deuda(deuda)
                .monto(new BigDecimal("1370")).fechaPago(LocalDate.of(2026, 7, 22)).build();

        when(pagoDeudaRepository.findByIdPagoDeudaForUpdate(25L)).thenReturn(Optional.of(pago));
        when(deudaRepository.findByIdForUpdate(66L)).thenReturn(Optional.of(deuda));
        when(pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(66L))
                .thenReturn(List.of());

        service.eliminarPago(25L);

        verify(pagoDeudaRepository).save(pago);
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).delete(any(PagoDeuda.class));
        assertThat(deuda.getActiva()).isFalse();
        assertThat(deuda.getMontoPagado()).isZero();
        assertThat(deuda.getMontoPendiente()).isEqualByComparingTo("1370");
        assertThat(deuda.getEstadoPago()).isEqualTo(EstadoPago.PENDIENTE);
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
        verify(pagoDeudaRepository, org.mockito.Mockito.never()).save(any(PagoDeuda.class));
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
