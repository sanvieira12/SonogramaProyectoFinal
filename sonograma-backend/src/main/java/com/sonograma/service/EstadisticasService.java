package com.sonograma.service;

import com.sonograma.dto.EstadisticaItemDTO;
import com.sonograma.dto.EstadisticasResponseDTO;
import com.sonograma.dto.IngresoSerieBucketDTO;
import com.sonograma.dto.IngresoSerieResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstadisticasService {

    private static final WeekFields ISO = WeekFields.ISO;

    private final VentaRepository ventaRepository;
    private final DiscoRepository discoRepository;
    private final PagoDeudaRepository pagoDeudaRepository;

    public EstadisticasResponseDTO obtenerCatalogoInventarioVentas() {
        List<Venta> ventas = ventaRepository.findAll().stream()
                .filter(v -> v.getEstado() != EstadoVenta.CANCELADA)
                .toList();
        List<Disco> discos = discoRepository.findAll();
        List<PagoDeuda> pagos = pagoDeudaRepository.findAll().stream()
                .filter(EstadisticasService::pagoVigente)
                .toList();
        Map<Long, BigDecimal> pagosPorVenta = pagos.stream()
                .filter(p -> p.getDeuda() != null && p.getDeuda().getVenta() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getDeuda().getVenta().getIdVenta(),
                        Collectors.reducing(BigDecimal.ZERO, PagoDeuda::getMonto, BigDecimal::add)));

        List<VentaDiscoItem> itemsVendidos = ventas.stream()
                .flatMap(venta -> ventaItems(venta).stream())
                .toList();

        return EstadisticasResponseDTO.builder()
                .aniosMusicaMasVendidos(agruparItemsVendidos(itemsVendidos, i -> valor(i.disco().getAnio()), i -> etiqueta(i.disco().getAnio()), true))
                .generosMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getGenero(), "Sin género"), i -> texto(i.disco().getGenero(), "Sin género"), true))
                .artistasMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getArtista(), "Sin artista"), i -> texto(i.disco().getArtista(), "Sin artista"), true))
                .sellosMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getSelloDiscografico(), "Sin sello"), i -> texto(i.disco().getSelloDiscografico(), "Sin sello"), true))
                .ventasPorMes(agruparIngresos(ventas, pagos, pagosPorVenta, FechaAgrupacion.MES))
                .ventasPorSemana(agruparIngresos(ventas, pagos, pagosPorVenta, FechaAgrupacion.SEMANA))
                .ventasPorAnio(agruparIngresos(ventas, pagos, pagosPorVenta, FechaAgrupacion.ANIO))
                .gananciaPorSemana(agruparVentas(ventas, v -> claveSemana(v.getFechaVenta()), v -> claveSemana(v.getFechaVenta()), false))
                .gananciaPorMes(agruparVentas(ventas, v -> claveMes(v.getFechaVenta()), v -> claveMes(v.getFechaVenta()), false))
                .gananciaPorAnio(agruparVentas(ventas, v -> claveAnio(v.getFechaVenta()), v -> claveAnio(v.getFechaVenta()), false))
                .inventarioPorEstado(agruparDiscos(discos, d -> d.getEstado() != null ? d.getEstado().name() : "SIN_ESTADO", d -> d.getEstado() != null ? labelEstado(d.getEstado().name()) : "Sin estado", true))
                .inventarioPorGenero(agruparDiscos(discos, d -> texto(d.getGenero(), "Sin género"), d -> texto(d.getGenero(), "Sin género"), true))
                .inventarioPorAnio(agruparDiscos(discos, d -> valor(d.getAnio()), d -> etiqueta(d.getAnio()), false))
                .inventarioPorSello(agruparDiscos(discos, d -> texto(d.getSelloDiscografico(), "Sin sello"), d -> texto(d.getSelloDiscografico(), "Sin sello"), true))
                .build();
    }

    public IngresoSerieResponseDTO obtenerSerieIngresos(String periodo) {
        SeriePeriodo seriePeriodo = SeriePeriodo.from(periodo);
        List<IngresoMovimiento> movimientos = ingresosVigentes();
        RangoPeriodo actual = seriePeriodo.rangoActual(LocalDate.now());
        RangoPeriodo anterior = seriePeriodo.rangoAnterior(actual);

        List<IngresoSerieBucketDTO> buckets = seriePeriodo.construirBuckets(actual, movimientos);
        ResumenIngresos resumenActual = resumenEnRango(actual, movimientos);
        BigDecimal totalActual = resumenActual.totalMonto();
        BigDecimal totalAnterior = resumenEnRango(anterior, movimientos).totalMonto();
        BigDecimal diferenciaMonto = totalActual.subtract(totalAnterior);
        BigDecimal diferenciaPorcentual = totalAnterior.compareTo(BigDecimal.ZERO) == 0
                ? null
                : diferenciaMonto.multiply(BigDecimal.valueOf(100))
                .divide(totalAnterior, 2, RoundingMode.HALF_UP);

        return IngresoSerieResponseDTO.builder()
                .periodo(seriePeriodo.name().toLowerCase(Locale.ROOT))
                .etiquetaPeriodo(seriePeriodo.etiqueta)
                .totalMonto(totalActual)
                .totalMontoPeriodoAnterior(totalAnterior)
                .diferenciaMonto(diferenciaMonto)
                .diferenciaPorcentual(diferenciaPorcentual)
                .cantidadVentas(resumenActual.cantidadVentas())
                .cantidadPagosDeuda(resumenActual.cantidadPagosDeuda())
                .buckets(buckets)
                .build();
    }

    private List<EstadisticaItemDTO> agruparIngresos(
            List<Venta> ventas, List<PagoDeuda> pagos, Map<Long, BigDecimal> pagosPorVenta,
            FechaAgrupacion agrupacion) {
        Map<String, Acumulador> acumulado = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            BigDecimal ingresoInicial = ingresoInicialVenta(
                    venta, pagosPorVenta.getOrDefault(venta.getIdVenta(), BigDecimal.ZERO));
            if (ingresoInicial.compareTo(BigDecimal.ZERO) <= 0) continue;
            String clave = clave(venta.getFechaVenta(), agrupacion);
            Acumulador acc = acumulado.computeIfAbsent(clave, k -> new Acumulador(k, k));
            acc.cantidad++;
            acc.totalMonto = acc.totalMonto.add(ingresoInicial);
        }
        for (PagoDeuda pago : pagos) {
            LocalDateTime fecha = pago.getFechaPago().atStartOfDay();
            String clave = clave(fecha, agrupacion);
            Acumulador acc = acumulado.computeIfAbsent(clave, k -> new Acumulador(k, k));
            acc.cantidadPagosDeuda++;
            acc.totalMonto = acc.totalMonto.add(pago.getMonto());
        }
        return ordenar(acumulado, false);
    }

    private List<IngresoMovimiento> ingresosVigentes() {
        List<Venta> ventas = ventaRepository.findAll().stream()
                .filter(v -> v.getEstado() != EstadoVenta.CANCELADA)
                .toList();
        List<PagoDeuda> pagos = pagoDeudaRepository.findAll().stream()
                .filter(EstadisticasService::pagoVigente)
                .toList();
        Map<Long, BigDecimal> pagosPorVenta = pagos.stream()
                .filter(p -> p.getDeuda() != null && p.getDeuda().getVenta() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getDeuda().getVenta().getIdVenta(),
                        Collectors.reducing(BigDecimal.ZERO, PagoDeuda::getMonto, BigDecimal::add)));

        List<IngresoMovimiento> movimientos = new ArrayList<>();
        for (Venta venta : ventas) {
            if (venta.getFechaVenta() == null) continue;
            BigDecimal ingresoInicial = ingresoInicialVenta(
                    venta, pagosPorVenta.getOrDefault(venta.getIdVenta(), BigDecimal.ZERO));
            if (ingresoInicial.compareTo(BigDecimal.ZERO) <= 0) continue;
            movimientos.add(new IngresoMovimiento(venta.getFechaVenta(), ingresoInicial, TipoIngreso.VENTA));
        }
        for (PagoDeuda pago : pagos) {
            movimientos.add(new IngresoMovimiento(pago.getFechaPago().atStartOfDay(), pago.getMonto(), TipoIngreso.PAGO_DEUDA));
        }
        movimientos.sort(Comparator.comparing(IngresoMovimiento::fecha));
        return movimientos;
    }

    private static ResumenIngresos resumenEnRango(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
        BigDecimal totalMonto = BigDecimal.ZERO;
        long cantidadVentas = 0;
        long cantidadPagosDeuda = 0;
        for (IngresoMovimiento movimiento : movimientos) {
            if (!rango.contiene(movimiento.fecha())) continue;
            totalMonto = totalMonto.add(movimiento.monto());
            if (movimiento.tipo() == TipoIngreso.VENTA) {
                cantidadVentas++;
            } else {
                cantidadPagosDeuda++;
            }
        }
        return new ResumenIngresos(totalMonto, cantidadVentas, cantidadPagosDeuda);
    }

    private static String clave(LocalDateTime fecha, FechaAgrupacion agrupacion) {
        return switch (agrupacion) {
            case MES -> claveMes(fecha);
            case SEMANA -> claveSemana(fecha);
            case ANIO -> claveAnio(fecha);
        };
    }

    private static BigDecimal ingresoInicialVenta(Venta venta, BigDecimal pagosPosteriores) {
        BigDecimal acumulado = venta.getMontoPagado() != null ? venta.getMontoPagado() : totalVenta(venta);
        BigDecimal inicial = acumulado.subtract(pagosPosteriores);
        return inicial.max(BigDecimal.ZERO);
    }

    private static boolean pagoVigente(PagoDeuda pago) {
        if (pago == null || pago.getMonto() == null || pago.getFechaPago() == null) return false;
        Venta venta = pago.getDeuda() != null ? pago.getDeuda().getVenta() : null;
        return venta == null || venta.getEstado() != EstadoVenta.CANCELADA;
    }

    private List<EstadisticaItemDTO> agruparVentas(
            List<Venta> ventas,
            Function<Venta, String> claveFn,
            Function<Venta, String> etiquetaFn,
            boolean ordenarPorCantidad
    ) {
        Map<String, Acumulador> acumulado = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            String clave = claveFn.apply(venta);
            Acumulador acc = acumulado.computeIfAbsent(clave, k -> new Acumulador(k, etiquetaFn.apply(venta)));
            acc.cantidad++;
            acc.totalMonto = acc.totalMonto.add(totalVenta(venta));
            acc.gananciaEstimada = acc.gananciaEstimada.add(gananciaVenta(venta));
        }
        return ordenar(acumulado, ordenarPorCantidad);
    }

    private List<EstadisticaItemDTO> agruparItemsVendidos(
            List<VentaDiscoItem> items,
            Function<VentaDiscoItem, String> claveFn,
            Function<VentaDiscoItem, String> etiquetaFn,
            boolean ordenarPorCantidad
    ) {
        Map<String, Acumulador> acumulado = new LinkedHashMap<>();
        for (VentaDiscoItem item : items) {
            String clave = claveFn.apply(item);
            Acumulador acc = acumulado.computeIfAbsent(clave, k -> new Acumulador(k, etiquetaFn.apply(item)));
            acc.cantidad++;
            acc.totalMonto = acc.totalMonto.add(item.precio());
            acc.gananciaEstimada = acc.gananciaEstimada.add(item.gananciaEstimada());
        }
        return ordenar(acumulado, ordenarPorCantidad);
    }

    private List<EstadisticaItemDTO> agruparDiscos(
            List<Disco> discos,
            Function<Disco, String> claveFn,
            Function<Disco, String> etiquetaFn,
            boolean ordenarPorCantidad
    ) {
        Map<String, Acumulador> acumulado = new LinkedHashMap<>();
        for (Disco disco : discos) {
            String clave = claveFn.apply(disco);
            Acumulador acc = acumulado.computeIfAbsent(clave, k -> new Acumulador(k, etiquetaFn.apply(disco)));
            acc.cantidad += Math.max(0, disco.getCantidadCopias() != null ? disco.getCantidadCopias() : 1);
        }
        return ordenar(acumulado, ordenarPorCantidad);
    }

    private List<VentaDiscoItem> ventaItems(Venta venta) {
        if (venta.getDetalles() != null && !venta.getDetalles().isEmpty()) {
            BigDecimal gananciaPorItem = venta.getDetalles().isEmpty()
                    ? BigDecimal.ZERO
                    : gananciaVenta(venta).divide(BigDecimal.valueOf(venta.getDetalles().size()), 2, java.math.RoundingMode.HALF_UP);
            return venta.getDetalles().stream()
                    .filter(detalle -> detalle.getDisco() != null)
                    .map(detalle -> new VentaDiscoItem(
                            detalle.getDisco(),
                            detalle.getPrecioUnitario() != null ? detalle.getPrecioUnitario() : BigDecimal.ZERO,
                            gananciaPorItem))
                    .toList();
        }
        if (venta.getDisco() == null) return List.of();
        return List.of(new VentaDiscoItem(venta.getDisco(), totalVenta(venta), gananciaVenta(venta)));
    }

    private List<EstadisticaItemDTO> ordenar(Map<String, Acumulador> acumulado, boolean ordenarPorCantidad) {
        Comparator<Acumulador> comparator = ordenarPorCantidad
                ? Comparator.comparingLong((Acumulador a) -> a.cantidad).reversed().thenComparing(a -> a.etiqueta)
                : Comparator.comparing(a -> a.clave);

        return acumulado.values().stream()
                .sorted(comparator)
                .map(Acumulador::toDTO)
                .collect(Collectors.toList());
    }

    private static BigDecimal totalVenta(Venta venta) {
        return VentaTotals.totalProductos(venta);
    }

    private static BigDecimal gananciaVenta(Venta venta) {
        return venta.getGananciaEstimada() != null ? venta.getGananciaEstimada() : BigDecimal.ZERO;
    }

    private static String claveMes(LocalDateTime fecha) {
        if (fecha == null) return "Sin fecha";
        return "%04d-%02d".formatted(fecha.getYear(), fecha.getMonthValue());
    }

    private static String claveSemana(LocalDateTime fecha) {
        if (fecha == null) return "Sin fecha";
        int semana = fecha.get(ISO.weekOfWeekBasedYear());
        int anioSemana = fecha.get(ISO.weekBasedYear());
        return "%04d-S%02d".formatted(anioSemana, semana);
    }

    private static String claveAnio(LocalDateTime fecha) {
        if (fecha == null) return "Sin fecha";
        return String.valueOf(fecha.getYear());
    }

    private static String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor.trim();
    }

    private static String valor(Integer valor) {
        return valor != null ? String.valueOf(valor) : "Sin año";
    }

    private static String etiqueta(Integer valor) {
        return valor != null ? String.valueOf(valor) : "Sin año";
    }

    private static String labelEstado(String estado) {
        return estado.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static class Acumulador {
        private final String clave;
        private final String etiqueta;
        private long cantidad = 0;
        private long cantidadPagosDeuda = 0;
        private BigDecimal totalMonto = BigDecimal.ZERO;
        private BigDecimal gananciaEstimada = BigDecimal.ZERO;

        private Acumulador(String clave, String etiqueta) {
            this.clave = clave;
            this.etiqueta = etiqueta;
        }

        private EstadisticaItemDTO toDTO() {
            return EstadisticaItemDTO.builder()
                    .clave(clave)
                    .etiqueta(etiqueta)
                    .cantidad(cantidad)
                    .cantidadPagosDeuda(cantidadPagosDeuda)
                    .totalMonto(totalMonto)
                    .gananciaEstimada(gananciaEstimada)
                    .build();
        }
    }

    private record IngresoMovimiento(LocalDateTime fecha, BigDecimal monto, TipoIngreso tipo) {}
    private record ResumenIngresos(BigDecimal totalMonto, long cantidadVentas, long cantidadPagosDeuda) {}
    private record VentaDiscoItem(Disco disco, BigDecimal precio, BigDecimal gananciaEstimada) {}
    private enum TipoIngreso { VENTA, PAGO_DEUDA }
    private enum FechaAgrupacion { SEMANA, MES, ANIO }

    private record RangoPeriodo(LocalDateTime inicio, LocalDateTime fin) {
        private boolean contiene(LocalDateTime fecha) {
            return fecha != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin);
        }
    }

    private enum SeriePeriodo {
        DIA("Día") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                return new RangoPeriodo(hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate diaAnterior = actual.inicio().toLocalDate().minusDays(1);
                return new RangoPeriodo(diaAnterior.atStartOfDay(), diaAnterior.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                List<IngresoSerieBucketDTO> buckets = new ArrayList<>();
                LocalDate dia = rango.inicio().toLocalDate();
                for (int hora = 0; hora < 24; hora++) {
                    LocalDateTime inicio = dia.atTime(hora, 0);
                    LocalDateTime fin = dia.atTime(hora, 59, 59);
                    buckets.add(bucket(
                            "%02d".formatted(hora),
                            "%02d h".formatted(hora),
                            totalEntre(movimientos, inicio, fin)
                    ));
                }
                return buckets;
            }
        },
        SEMANA("Semana") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                LocalDate inicio = hoy.with(DayOfWeek.MONDAY);
                LocalDate fin = inicio.plusDays(6);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate inicio = actual.inicio().toLocalDate().minusWeeks(1);
                LocalDate fin = inicio.plusDays(6);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                List<IngresoSerieBucketDTO> buckets = new ArrayList<>();
                LocalDate cursor = rango.inicio().toLocalDate();
                while (!cursor.isAfter(rango.fin().toLocalDate())) {
                    buckets.add(bucket(
                            cursor.toString(),
                            diaSemana(cursor.getDayOfWeek()),
                            totalEntre(movimientos, cursor.atStartOfDay(), cursor.atTime(LocalTime.MAX))
                    ));
                    cursor = cursor.plusDays(1);
                }
                return buckets;
            }
        },
        MES("Mes") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                LocalDate inicio = hoy.withDayOfMonth(1);
                LocalDate fin = hoy.withDayOfMonth(hoy.lengthOfMonth());
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate inicio = actual.inicio().toLocalDate().minusMonths(1).withDayOfMonth(1);
                LocalDate fin = inicio.withDayOfMonth(inicio.lengthOfMonth());
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                List<IngresoSerieBucketDTO> buckets = new ArrayList<>();
                LocalDate cursor = rango.inicio().toLocalDate();
                while (!cursor.isAfter(rango.fin().toLocalDate())) {
                    buckets.add(bucket(
                            cursor.toString(),
                            String.valueOf(cursor.getDayOfMonth()),
                            totalEntre(movimientos, cursor.atStartOfDay(), cursor.atTime(LocalTime.MAX))
                    ));
                    cursor = cursor.plusDays(1);
                }
                return buckets;
            }
        },
        TRIMESTRE("Trimestre") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                LocalDate inicio = inicioTrimestre(hoy);
                LocalDate fin = inicio.plusMonths(3).minusDays(1);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate inicio = actual.inicio().toLocalDate().minusMonths(3);
                LocalDate fin = actual.fin().toLocalDate().minusMonths(3);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                return bucketsSemanales(rango, movimientos);
            }
        },
        SEMESTRE("Semestre") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                LocalDate inicio = hoy.getMonthValue() <= 6 ? LocalDate.of(hoy.getYear(), 1, 1) : LocalDate.of(hoy.getYear(), 7, 1);
                LocalDate fin = inicio.plusMonths(6).minusDays(1);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate inicio = actual.inicio().toLocalDate().minusMonths(6);
                LocalDate fin = actual.fin().toLocalDate().minusMonths(6);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                return bucketsMensuales(rango, movimientos);
            }
        },
        ANIO("Año") {
            @Override
            RangoPeriodo rangoActual(LocalDate hoy) {
                LocalDate inicio = LocalDate.of(hoy.getYear(), 1, 1);
                LocalDate fin = LocalDate.of(hoy.getYear(), 12, 31);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            RangoPeriodo rangoAnterior(RangoPeriodo actual) {
                LocalDate inicio = actual.inicio().toLocalDate().minusYears(1);
                LocalDate fin = actual.fin().toLocalDate().minusYears(1);
                return new RangoPeriodo(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX));
            }

            @Override
            List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
                return bucketsMensuales(rango, movimientos);
            }
        };

        private final String etiqueta;

        SeriePeriodo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        abstract RangoPeriodo rangoActual(LocalDate hoy);
        abstract RangoPeriodo rangoAnterior(RangoPeriodo actual);
        abstract List<IngresoSerieBucketDTO> construirBuckets(RangoPeriodo rango, List<IngresoMovimiento> movimientos);

        private static SeriePeriodo from(String valor) {
            if (valor == null || valor.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
            }
            try {
                return valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
            }
        }
    }

    private static List<IngresoSerieBucketDTO> bucketsMensuales(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
        List<IngresoSerieBucketDTO> buckets = new ArrayList<>();
        LocalDate cursor = rango.inicio().toLocalDate().withDayOfMonth(1);
        LocalDate fin = rango.fin().toLocalDate();
        while (!cursor.isAfter(fin)) {
            LocalDate inicioMes = cursor.withDayOfMonth(1);
            LocalDate finMes = cursor.withDayOfMonth(cursor.lengthOfMonth());
            buckets.add(bucket(
                    "%04d-%02d".formatted(cursor.getYear(), cursor.getMonthValue()),
                    nombreMes(cursor.getMonth()),
                    totalEntre(movimientos, inicioMes.atStartOfDay(), finMes.atTime(LocalTime.MAX))
            ));
            cursor = cursor.plusMonths(1);
        }
        return buckets;
    }

    private static List<IngresoSerieBucketDTO> bucketsSemanales(RangoPeriodo rango, List<IngresoMovimiento> movimientos) {
        List<IngresoSerieBucketDTO> buckets = new ArrayList<>();
        LocalDate cursor = rango.inicio().toLocalDate();
        int index = 1;
        while (!cursor.isAfter(rango.fin().toLocalDate())) {
            LocalDate finSemana = min(cursor.plusDays(6), rango.fin().toLocalDate());
            buckets.add(bucket(
                    cursor.toString(),
                    "Sem " + index,
                    totalEntre(movimientos, cursor.atStartOfDay(), finSemana.atTime(LocalTime.MAX))
            ));
            cursor = finSemana.plusDays(1);
            index++;
        }
        return buckets;
    }

    private static IngresoSerieBucketDTO bucket(String clave, String etiqueta, BigDecimal totalMonto) {
        return IngresoSerieBucketDTO.builder()
                .clave(clave)
                .etiqueta(etiqueta)
                .totalMonto(totalMonto)
                .build();
    }

    private static BigDecimal totalEntre(List<IngresoMovimiento> movimientos, LocalDateTime inicio, LocalDateTime fin) {
        return movimientos.stream()
                .filter(movimiento -> !movimiento.fecha().isBefore(inicio) && !movimiento.fecha().isAfter(fin))
                .map(IngresoMovimiento::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate inicioTrimestre(LocalDate fecha) {
        int mesInicio = ((fecha.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(fecha.getYear(), mesInicio, 1);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String nombreMes(Month month) {
        return month.getDisplayName(java.time.format.TextStyle.SHORT, new Locale("es", "UY"));
    }

    private static String diaSemana(DayOfWeek dayOfWeek) {
        return dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, new Locale("es", "UY"));
    }
}
