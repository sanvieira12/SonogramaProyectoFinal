package com.sonograma.service;

import com.sonograma.dto.EstadisticaItemDTO;
import com.sonograma.dto.EstadisticasResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
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

    public EstadisticasResponseDTO obtenerCatalogoInventarioVentas() {
        List<Venta> ventas = ventaRepository.findAll().stream()
                .filter(v -> v.getEstado() != EstadoVenta.CANCELADA)
                .toList();
        List<Disco> discos = discoRepository.findAll();

        List<VentaDiscoItem> itemsVendidos = ventas.stream()
                .flatMap(venta -> ventaItems(venta).stream())
                .toList();

        return EstadisticasResponseDTO.builder()
                .aniosMusicaMasVendidos(agruparItemsVendidos(itemsVendidos, i -> valor(i.disco().getAnio()), i -> etiqueta(i.disco().getAnio()), true))
                .generosMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getGenero(), "Sin género"), i -> texto(i.disco().getGenero(), "Sin género"), true))
                .artistasMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getArtista(), "Sin artista"), i -> texto(i.disco().getArtista(), "Sin artista"), true))
                .sellosMasVendidos(agruparItemsVendidos(itemsVendidos, i -> texto(i.disco().getSelloDiscografico(), "Sin sello"), i -> texto(i.disco().getSelloDiscografico(), "Sin sello"), true))
                .ventasPorMes(agruparVentas(ventas, v -> claveMes(v.getFechaVenta()), v -> claveMes(v.getFechaVenta()), false))
                .ventasPorSemana(agruparVentas(ventas, v -> claveSemana(v.getFechaVenta()), v -> claveSemana(v.getFechaVenta()), false))
                .ventasPorAnio(agruparVentas(ventas, v -> claveAnio(v.getFechaVenta()), v -> claveAnio(v.getFechaVenta()), false))
                .gananciaPorSemana(agruparVentas(ventas, v -> claveSemana(v.getFechaVenta()), v -> claveSemana(v.getFechaVenta()), false))
                .gananciaPorMes(agruparVentas(ventas, v -> claveMes(v.getFechaVenta()), v -> claveMes(v.getFechaVenta()), false))
                .gananciaPorAnio(agruparVentas(ventas, v -> claveAnio(v.getFechaVenta()), v -> claveAnio(v.getFechaVenta()), false))
                .inventarioPorEstado(agruparDiscos(discos, d -> d.getEstado() != null ? d.getEstado().name() : "SIN_ESTADO", d -> d.getEstado() != null ? labelEstado(d.getEstado().name()) : "Sin estado", true))
                .inventarioPorGenero(agruparDiscos(discos, d -> texto(d.getGenero(), "Sin género"), d -> texto(d.getGenero(), "Sin género"), true))
                .inventarioPorAnio(agruparDiscos(discos, d -> valor(d.getAnio()), d -> etiqueta(d.getAnio()), false))
                .inventarioPorSello(agruparDiscos(discos, d -> texto(d.getSelloDiscografico(), "Sin sello"), d -> texto(d.getSelloDiscografico(), "Sin sello"), true))
                .build();
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
        if (venta.getTotalFinal() != null) return venta.getTotalFinal();
        if (venta.getTotal() != null) return venta.getTotal();
        return BigDecimal.ZERO;
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
                    .totalMonto(totalMonto)
                    .gananciaEstimada(gananciaEstimada)
                    .build();
        }
    }

    private record VentaDiscoItem(Disco disco, BigDecimal precio, BigDecimal gananciaEstimada) {}
}
