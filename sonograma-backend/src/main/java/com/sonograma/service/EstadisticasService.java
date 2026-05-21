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

        return EstadisticasResponseDTO.builder()
                .aniosMusicaMasVendidos(agruparVentas(ventas, v -> valor(v.getDisco().getAnio()), v -> etiqueta(v.getDisco().getAnio()), true))
                .generosMasVendidos(agruparVentas(ventas, v -> texto(v.getDisco().getGenero(), "Sin género"), v -> texto(v.getDisco().getGenero(), "Sin género"), true))
                .artistasMasVendidos(agruparVentas(ventas, v -> texto(v.getDisco().getArtista(), "Sin artista"), v -> texto(v.getDisco().getArtista(), "Sin artista"), true))
                .sellosMasVendidos(agruparVentas(ventas, v -> texto(v.getDisco().getSelloDiscografico(), "Sin sello"), v -> texto(v.getDisco().getSelloDiscografico(), "Sin sello"), true))
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
            acc.cantidad++;
        }
        return ordenar(acumulado, ordenarPorCantidad);
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
}
