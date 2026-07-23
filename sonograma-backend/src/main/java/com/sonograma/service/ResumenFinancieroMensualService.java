package com.sonograma.service;

import com.sonograma.dto.GastoTiendaDTO;
import com.sonograma.dto.ItemResumenMensualDTO;
import com.sonograma.dto.ResumenFinancieroMensualDTO;
import com.sonograma.dto.VentaResumenMensualDTO;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.GastoTienda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.GastoTiendaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Single monthly source for the Sales Ledger UI summary. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumenFinancieroMensualService {

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final VentaRepository ventaRepository;
    private final PagoDeudaRepository pagoDeudaRepository;
    private final GastoTiendaRepository gastoTiendaRepository;
    private final ProfitCalculationService profitCalculationService;
    private final IngresoLibroCalculator ingresoLibroCalculator;

    public ResumenFinancieroMensualDTO obtener(String periodo) {
        PeriodoSeleccionado selected = seleccionarPeriodo(periodo);
        List<Venta> ventas = ventaRepository.findAllForProfitPeriod(selected.desde.atStartOfDay(), selected.hasta.atTime(23, 59, 59, 999_999_999));
        List<PagoDeuda> pagos = pagoDeudaRepository.findValidosEntre(selected.desde, selected.hasta);
        List<GastoTienda> gastos = gastoTiendaRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(selected.desde, selected.hasta);

        List<VentaResumenMensualDTO> ventasDTO = new ArrayList<>();
        List<ItemResumenMensualDTO> itemsDTO = new ArrayList<>();
        BigDecimal totalVentas = ZERO;
        BigDecimal ingresosVentas = ZERO;
        BigDecimal ganancia = ZERO;
        long cantidadItems = 0;
        int faltantes = 0;

        for (Venta venta : ventas) {
            ProfitResult profit = profitCalculationService.netProfitForSale(venta);
            totalVentas = totalVentas.add(VentaTotals.totalProductos(venta));
            ingresosVentas = ingresosVentas.add(ingresoVentaEnFechaDeVenta(venta));
            ganancia = ganancia.add(nvl(profit.netProfit()));
            faltantes += profit.affectedItemCount();

            if (venta.getDetalles() != null && !venta.getDetalles().isEmpty()) {
                List<ProfitItemResult> itemResults = profit.items();
                for (int index = 0; index < venta.getDetalles().size(); index++) {
                    DetalleVenta detalle = venta.getDetalles().get(index);
                    ProfitItemResult item = index < itemResults.size() ? itemResults.get(index) : null;
                    cantidadItems += cantidad(detalle);
                    itemsDTO.add(itemDto(venta, detalle, item));
                }
            } else if (!profit.items().isEmpty()) {
                ProfitItemResult item = profit.items().get(0);
                cantidadItems += item.quantity();
                itemsDTO.add(ItemResumenMensualDTO.builder()
                        .idVenta(venta.getIdVenta())
                        .artista(venta.getDisco() != null ? venta.getDisco().getArtista() : null)
                        .album(venta.getDisco() != null ? venta.getDisco().getAlbum() : null)
                        .codigoInterno(venta.getDisco() != null ? venta.getDisco().getCodigoInterno() : null)
                        .cantidad(item.quantity())
                        .importeVentaReal(item.actualSaleAmount())
                        .costoAdquisicionOriginal(item.acquisitionCost())
                        .gananciaNeta(item.netProfit())
                        .grossProfit(item.grossProfit())
                        .detailGrossProfit(item.grossProfit())
                        .estadoGanancia(item.status().name())
                        .build());
            }

            ventasDTO.add(VentaResumenMensualDTO.builder()
                    .idVenta(venta.getIdVenta())
                    .fecha(venta.getFechaVenta())
                    .cliente(nombreCliente(venta))
                    .numeroRecibo(venta.getNumeroRecibo())
                    .estadoPago(venta.getEstadoPago() != null ? venta.getEstadoPago().name() : null)
                    .totalVenta(VentaTotals.totalProductos(venta))
                    .montoRecibido(ingresoVentaEnFechaDeVenta(venta))
                    .deudaPendiente(nvl(venta.getMontoDeuda()))
                    .gananciaNeta(profit.netProfit())
                    .grossProfit(profit.grossProfit())
                    .saleGrossProfit(profit.grossProfit())
                    .grossProfitAvailable(profit.grossProfitAvailable())
                    .grossProfitUnavailableReason(profit.affectedItemCount() > 0
                            ? "Uno o más ítems no tienen un costo histórico confiable" : null)
                    .estadoGanancia(profit.status().name())
                    .build());
        }

        BigDecimal ingresosPagos = pagos.stream().map(PagoDeuda::getMonto).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
        BigDecimal ingresosRegistrados = ingresosVentas.add(ingresosPagos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalGastos = gastos.stream().map(GastoTienda::getMonto).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);

        return ResumenFinancieroMensualDTO.builder()
                .periodo(selected.periodo)
                .desde(selected.desde)
                .hasta(selected.hasta)
                .cantidadVentas((long) ventas.size())
                .cantidadItems(cantidadItems)
                .totalVentas(totalVentas)
                .ingresosRegistrados(ingresosRegistrados)
                .gananciaItems(ganancia)
                .gastos(totalGastos)
                .balanceFinal(ingresosRegistrados)
                .itemsGananciaNoDisponible(faltantes)
                .advertenciaGanancia(faltantes > 0
                        ? faltantes + " ítem(s) no tienen un costo de adquisición histórico válido; su ganancia no fue inventada ni incluida."
                        : null)
                .ventas(ventasDTO)
                .items(itemsDTO)
                .gastosDetalle(gastos.stream().map(this::gastoDto).toList())
                .build();
    }

    private BigDecimal ingresoVentaEnFechaDeVenta(Venta venta) {
        if (venta.getMontoPagado() != null) return money(venta.getMontoPagado());
        return venta.getEstadoPago() == EstadoPago.PAGADO ? money(ingresoLibroCalculator.montoVenta(venta)) : ZERO;
    }

    private ItemResumenMensualDTO itemDto(Venta venta, DetalleVenta detalle, ProfitItemResult item) {
        return ItemResumenMensualDTO.builder()
                .idVenta(venta.getIdVenta())
                .artista(detalle.getArtistaSnap() != null ? detalle.getArtistaSnap() : detalle.getDisco() != null ? detalle.getDisco().getArtista() : null)
                .album(detalle.getAlbumSnap() != null ? detalle.getAlbumSnap() : detalle.getDisco() != null ? detalle.getDisco().getAlbum() : null)
                .codigoInterno(detalle.getCodigoSnap() != null ? detalle.getCodigoSnap() : detalle.getDisco() != null ? detalle.getDisco().getCodigoInterno() : null)
                .cantidad(cantidad(detalle))
                .importeVentaReal(item != null ? item.actualSaleAmount() : null)
                .costoAdquisicionOriginal(item != null ? item.acquisitionCost() : null)
                .gananciaNeta(item != null ? item.netProfit() : null)
                .grossProfit(item != null ? item.grossProfit() : null)
                .detailGrossProfit(item != null ? item.grossProfit() : null)
                .estadoGanancia(item != null ? item.status().name() : ProfitStatus.UNAVAILABLE.name())
                .build();
    }

    private GastoTiendaDTO gastoDto(GastoTienda gasto) {
        return GastoTiendaDTO.builder().idGasto(gasto.getIdGasto()).fecha(gasto.getFecha())
                .descripcion(gasto.getDescripcion()).monto(gasto.getMonto()).categoria(gasto.getCategoria()).build();
    }

    private String nombreCliente(Venta venta) {
        if (venta.getClienteNombreSnapshot() != null && !venta.getClienteNombreSnapshot().isBlank()) return venta.getClienteNombreSnapshot();
        if (venta.getCliente() == null) return "—";
        return (nvlText(venta.getCliente().getNombre()) + " " + nvlText(venta.getCliente().getApellido())).trim();
    }

    private PeriodoSeleccionado seleccionarPeriodo(String raw) {
        LocalDate hoy = LocalDate.now(URUGUAY);
        YearMonth current = YearMonth.from(hoy);
        YearMonth selected;
        try {
            selected = raw == null || raw.isBlank() ? current : YearMonth.parse(raw, PERIOD_FORMAT);
        } catch (RuntimeException exception) {
            throw new NegocioException("El período debe tener formato yyyy-MM");
        }
        if (selected.isAfter(current)) throw new NegocioException("No se puede consultar un mes futuro");
        LocalDate desde = selected.atDay(1);
        LocalDate hasta = selected.equals(current) ? hoy : selected.atEndOfMonth();
        return new PeriodoSeleccionado(selected.format(PERIOD_FORMAT), desde, hasta);
    }

    private int cantidad(DetalleVenta detalle) { return detalle.getCantidad() != null && detalle.getCantidad() > 0 ? detalle.getCantidad() : 1; }
    private BigDecimal nvl(BigDecimal value) { return value == null ? ZERO : value; }
    private BigDecimal money(BigDecimal value) { return nvl(value).setScale(2, RoundingMode.HALF_UP); }
    private String nvlText(String value) { return value == null ? "" : value; }

    private record PeriodoSeleccionado(String periodo, LocalDate desde, LocalDate hasta) {}
}
