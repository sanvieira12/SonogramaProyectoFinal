package com.sonograma.service;

import com.sonograma.dto.DetalleVentaDTO;
import com.sonograma.dto.DetalleVentaResponseDTO;
import com.sonograma.dto.EnvioDTO;
import com.sonograma.dto.ConfiguracionCostosDTO;
import com.sonograma.dto.ResultadoCostoVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.dto.VentasPorMesDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.DireccionCliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.Envio;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.CanalVenta;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.MedioPago;
import com.sonograma.enums.TipoEntrega;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DireccionClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final EnvioRepository envioRepository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;
    private final DireccionClienteRepository direccionClienteRepository;
    private final DeudaRepository deudaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final PagoDeudaRepository pagoDeudaRepository;
    private final ClienteService clienteService;
    private final DeudaService deudaService;
    private final CostosVentaService costosVentaService;
    private final DiscoQrCopyService discoQrCopyService;
    private final DiscoEstadoService discoEstadoService;
    private final IngresoLibroCalculator ingresoLibroCalculator;

    private static final BigDecimal CIEN = new BigDecimal("100");

    public VentaResponseDTO registrarVenta(VentaRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", dto.getIdCliente()));

        boolean tieneDetalles = dto.getDetalles() != null && !dto.getDetalles().isEmpty();

        CanalVenta canal = parseCanal(dto.getCanalVenta());
        TipoEntrega entrega = parseEntrega(dto.getTipoEntrega());
        LocalDateTime fechaVenta = dto.getFechaVenta() != null ? dto.getFechaVenta() : LocalDateTime.now();
        String numeroFactura = generarNumeroFactura(fechaVenta.getYear());
        String clienteSnapshot = cliente.getNombre() + (cliente.getApellido() != null ? " " + cliente.getApellido() : "");

        MedioPago medioPago = parseMedioPago(dto.getMedioPago());

        ResultadoCostoVentaDTO costos;
        Disco discoLegacy = null;
        List<PreparedDetalle> detallesPreparados = new ArrayList<>();
        BigDecimal subtotalDetalles = BigDecimal.ZERO;
        BigDecimal descuentoPct = BigDecimal.ZERO;

        if (tieneDetalles) {
            descuentoPct = dto.getDescuentoPorcentaje() != null ? dto.getDescuentoPorcentaje() : BigDecimal.ZERO;
            for (DetalleVentaDTO d : dto.getDetalles()) {
                PreparedDetalle preparado = prepararDetalle(d);
                detallesPreparados.add(preparado);
                subtotalDetalles = subtotalDetalles.add(
                        preparado.precioUnitario().multiply(BigDecimal.valueOf(preparado.cantidad()))
                );
            }
            BigDecimal factor = CIEN.subtract(descuentoPct).divide(CIEN, 4, RoundingMode.HALF_UP);
            BigDecimal precioVentaNet = subtotalDetalles.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costoDiTotal = detallesPreparados.stream()
                    .map(PreparedDetalle::costoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            costos = costosVentaService.calcular(costoDiTotal, precioVentaNet, dto);
        } else {
            if (dto.getIdDisco() == null) throw new NegocioException("Especificá un disco o al menos un detalle de venta");
            discoLegacy = discoRepository.findById(dto.getIdDisco())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Disco", dto.getIdDisco()));
            discoQrCopyService.synchronize(discoLegacy);
            validarStockDisponible(discoLegacy);
            costos = costosVentaService.calcular(discoLegacy, dto);
        }

        BigDecimal montoPagado = calcularMontoPagado(dto, costos.getTotalFinal());
        BigDecimal montoDeuda = costos.getTotalFinal().subtract(montoPagado);
        if (montoDeuda.compareTo(BigDecimal.ZERO) < 0) montoDeuda = BigDecimal.ZERO;
        EstadoPago estadoPago = montoDeuda.compareTo(BigDecimal.ZERO) == 0 ? EstadoPago.PAGADO
                : montoPagado.compareTo(BigDecimal.ZERO) == 0 ? EstadoPago.PENDIENTE : EstadoPago.PARCIAL;

        Venta venta = Venta.builder()
                .cliente(cliente)
                .disco(discoLegacy)
                .fechaVenta(fechaVenta)
                .canalVenta(canal)
                .total(costos.getTotalFinal())
                .costoDisco(costos.getCostoDisco())
                .precioVenta(costos.getPrecioVenta())
                .costoEnvio(costos.getCostoEnvio())
                .porcentajeImpuesto(costos.getPorcentajeImpuesto())
                .montoImpuesto(costos.getMontoImpuesto())
                .otrosCostos(costos.getOtrosCostos())
                .totalFinal(costos.getTotalFinal())
                .gananciaEstimada(costos.getGananciaEstimada())
                .subtotal(tieneDetalles ? subtotalDetalles : null)
                .descuentoPorcentaje(tieneDetalles ? descuentoPct : null)
                .tipoEntrega(entrega)
                .estado(EstadoVenta.COMPLETADA)
                .observaciones(dto.getObservaciones())
                .numeroFactura(numeroFactura)
                .numeroRecibo(textoNulo(dto.getNumeroRecibo()))
                .clienteNombreSnapshot(clienteSnapshot)
                .medioPago(medioPago)
                .montoPagado(montoPagado)
                .montoDeuda(montoDeuda)
                .estadoPago(estadoPago)
                .build();

        venta = ventaRepository.save(venta);

        if (tieneDetalles) {
            for (PreparedDetalle preparado : detallesPreparados) {
                String copyIdsSnapshot = reservarStock(preparado);
                DetalleVenta detalle = detalleDesdePreparado(venta, preparado, copyIdsSnapshot);
                venta.getDetalles().add(detalleVentaRepository.save(detalle));
            }
        } else {
            reservarStock(new PreparedDetalle(discoLegacy, dto.getPrecioVenta(), 1, false, null, null, null, null, BigDecimal.ZERO, null, null));
        }

        deudaService.sincronizarVenta(venta, cliente, costos.getTotalFinal(), montoPagado,
                montoDeuda, estadoPago, fechaVenta);

        Envio envio = null;
        if (entrega == TipoEntrega.ENVIO) {
            DireccionCliente direccionCliente = resolverDireccionCliente(cliente, dto);
            String direccionCompleta = armarDireccionEnvio(direccionCliente, dto);
            envio = Envio.builder()
                    .venta(venta)
                    .direccionEnvio(direccionCompleta)
                    .departamento(dto.getDepartamento())
                    .sucursalDacCodigo(dto.getSucursalDacCodigo())
                    .sucursalDacNombre(dto.getSucursalDacNombre())
                    .costoEnvio(costos.getCostoEnvio())
                    .estadoLogistico("PREPARANDO")
                    .build();
            envio = envioRepository.save(envio);
        }

        return mapearADTO(venta, envio);
    }

    public VentaResponseDTO actualizarVenta(Long id, VentaRequestDTO dto) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        if (venta.getEstado() == EstadoVenta.CANCELADA) {
            throw new NegocioException("No se puede editar una venta cancelada");
        }

        restaurarStockVenta(venta);
        detalleVentaRepository.deleteAll(new ArrayList<>(venta.getDetalles()));
        venta.getDetalles().clear();
        venta.setDisco(null);

        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", dto.getIdCliente()));

        boolean tieneDetalles = dto.getDetalles() != null && !dto.getDetalles().isEmpty();
        CanalVenta canal = parseCanal(dto.getCanalVenta());
        TipoEntrega entrega = parseEntrega(dto.getTipoEntrega());
        LocalDateTime fechaVenta = dto.getFechaVenta() != null ? dto.getFechaVenta() : venta.getFechaVenta();
        String clienteSnapshot = cliente.getNombre() + (cliente.getApellido() != null ? " " + cliente.getApellido() : "");
        MedioPago medioPago = parseMedioPago(dto.getMedioPago());

        ResultadoCostoVentaDTO costos;
        Disco discoLegacy = null;
        List<PreparedDetalle> detallesPreparados = new ArrayList<>();
        BigDecimal subtotalDetalles = BigDecimal.ZERO;
        BigDecimal descuentoPct = dto.getDescuentoPorcentaje() != null ? dto.getDescuentoPorcentaje() : BigDecimal.ZERO;

        if (tieneDetalles) {
            for (DetalleVentaDTO d : dto.getDetalles()) {
                PreparedDetalle preparado = prepararDetalle(d);
                detallesPreparados.add(preparado);
                subtotalDetalles = subtotalDetalles.add(
                        preparado.precioUnitario().multiply(BigDecimal.valueOf(preparado.cantidad()))
                );
            }
            BigDecimal factor = CIEN.subtract(descuentoPct).divide(CIEN, 4, RoundingMode.HALF_UP);
            BigDecimal precioVentaNet = subtotalDetalles.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costoDiscoTotal = detallesPreparados.stream()
                    .map(PreparedDetalle::costoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            costos = costosVentaService.calcular(costoDiscoTotal, precioVentaNet, dto);
        } else {
            if (dto.getIdDisco() == null) throw new NegocioException("Especificá un disco o al menos un detalle de venta");
            discoLegacy = discoRepository.findById(dto.getIdDisco())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Disco", dto.getIdDisco()));
            discoQrCopyService.synchronize(discoLegacy);
            validarStockDisponible(discoLegacy);
            costos = costosVentaService.calcular(discoLegacy, dto);
        }

        BigDecimal montoPagado = calcularMontoPagado(dto, costos.getTotalFinal());
        BigDecimal montoDeuda = costos.getTotalFinal().subtract(montoPagado);
        if (montoDeuda.compareTo(BigDecimal.ZERO) < 0) montoDeuda = BigDecimal.ZERO;
        EstadoPago estadoPago = montoDeuda.compareTo(BigDecimal.ZERO) == 0 ? EstadoPago.PAGADO
                : montoPagado.compareTo(BigDecimal.ZERO) == 0 ? EstadoPago.PENDIENTE : EstadoPago.PARCIAL;

        venta.setCliente(cliente);
        venta.setDisco(discoLegacy);
        venta.setFechaVenta(fechaVenta);
        venta.setCanalVenta(canal);
        venta.setTotal(costos.getTotalFinal());
        venta.setCostoDisco(costos.getCostoDisco());
        venta.setPrecioVenta(costos.getPrecioVenta());
        venta.setCostoEnvio(costos.getCostoEnvio());
        venta.setPorcentajeImpuesto(costos.getPorcentajeImpuesto());
        venta.setMontoImpuesto(costos.getMontoImpuesto());
        venta.setOtrosCostos(costos.getOtrosCostos());
        venta.setTotalFinal(costos.getTotalFinal());
        venta.setGananciaEstimada(costos.getGananciaEstimada());
        venta.setSubtotal(tieneDetalles ? subtotalDetalles : null);
        venta.setDescuentoPorcentaje(tieneDetalles ? descuentoPct : null);
        venta.setTipoEntrega(entrega);
        venta.setEstado(EstadoVenta.COMPLETADA);
        venta.setObservaciones(dto.getObservaciones());
        venta.setNumeroRecibo(textoNulo(dto.getNumeroRecibo()));
        venta.setClienteNombreSnapshot(clienteSnapshot);
        venta.setMedioPago(medioPago);
        venta.setMontoPagado(montoPagado);
        venta.setMontoDeuda(montoDeuda);
        venta.setEstadoPago(estadoPago);
        venta = ventaRepository.save(venta);

        if (tieneDetalles) {
            for (PreparedDetalle preparado : detallesPreparados) {
                String copyIdsSnapshot = reservarStock(preparado);
                DetalleVenta detalle = detalleDesdePreparado(venta, preparado, copyIdsSnapshot);
                venta.getDetalles().add(detalleVentaRepository.save(detalle));
            }
        } else {
            reservarStock(new PreparedDetalle(discoLegacy, dto.getPrecioVenta(), 1, false, null, null, null, null, BigDecimal.ZERO, null, null));
        }

        deudaService.sincronizarVenta(venta, cliente, costos.getTotalFinal(), montoPagado,
                montoDeuda, estadoPago, fechaVenta);
        Envio envio = sincronizarEnvio(venta, cliente, dto, entrega, costos);
        return mapearADTO(venta, envio);
    }

    public void cancelarVenta(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        if ("PRE_VENTA".equals(venta.getOrigen())) {
            throw new NegocioException("El cobro de una pre-venta debe gestionarse desde Pre-ventas");
        }
        if (venta.getEstado() == EstadoVenta.CANCELADA) return;
        restaurarStockVenta(venta);
        venta.setEstado(EstadoVenta.CANCELADA);
        ventaRepository.save(venta);
        deudaRepository.findByVentaIdVentaAndActivaTrue(id).ifPresent(deuda -> {
            deuda.setActiva(false);
            deuda.setUpdatedAt(LocalDateTime.now());
            deudaRepository.save(deuda);
        });
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> obtenerTodas() {
        return ventaRepository.findAllByOrderByFechaVentaDesc().stream()
                .map(v -> mapearADTO(v, envioRepository.findByVentaIdVenta(v.getIdVenta()).orElse(null)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        Envio envio = envioRepository.findByVentaIdVenta(id).orElse(null);
        return mapearADTO(venta, envio);
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> obtenerPorCliente(Long idCliente) {
        return ventaRepository.findByClienteIdClienteOrderByFechaVentaDesc(idCliente).stream()
                .map(v -> mapearADTO(v, envioRepository.findByVentaIdVenta(v.getIdVenta()).orElse(null)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VentasPorMesDTO> obtenerEstadisticasPorMes() {
        record MesIngreso(long cantidad, BigDecimal total) {
            MesIngreso sumarVenta(BigDecimal monto) { return new MesIngreso(cantidad + 1, total.add(monto)); }
            MesIngreso sumarPago(BigDecimal monto) { return new MesIngreso(cantidad, total.add(monto)); }
        }
        java.util.Map<String, MesIngreso> meses = new TreeMap<>();
        List<PagoDeuda> pagos = pagoDeudaRepository.findAll();
        java.util.Map<Long, BigDecimal> pagosPorVenta = pagos.stream()
                .filter(p -> p.getDeuda() != null && p.getDeuda().getVenta() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getDeuda().getVenta().getIdVenta(),
                        Collectors.reducing(BigDecimal.ZERO, PagoDeuda::getMonto, BigDecimal::add)));
        ventaRepository.findAll().stream()
                .filter(v -> v.getEstado() != EstadoVenta.CANCELADA)
                .forEach(v -> {
                    String mes = "%04d-%02d".formatted(v.getFechaVenta().getYear(), v.getFechaVenta().getMonthValue());
                    BigDecimal acumulado = v.getMontoPagado() != null ? v.getMontoPagado() : VentaTotals.totalProductos(v);
                    BigDecimal ingreso = acumulado
                            .subtract(pagosPorVenta.getOrDefault(v.getIdVenta(), BigDecimal.ZERO))
                            .max(BigDecimal.ZERO);
                    meses.put(mes, meses.getOrDefault(mes, new MesIngreso(0, BigDecimal.ZERO)).sumarVenta(ingreso));
                });
        pagos.forEach(p -> {
            LocalDate fecha = ingresoLibroCalculator.fechaPago(p).toLocalDate();
            String mes = "%04d-%02d".formatted(fecha.getYear(), fecha.getMonthValue());
            meses.put(mes, meses.getOrDefault(mes, new MesIngreso(0, BigDecimal.ZERO)).sumarPago(p.getMonto()));
        });
        return meses.entrySet().stream()
                .map(e -> VentasPorMesDTO.builder()
                        .mes(e.getKey()).etiqueta(etiquetaMes(e.getKey()))
                        .cantidad(e.getValue().cantidad()).totalMonto(e.getValue().total()).build())
                .toList();
    }

    private String etiquetaMes(String mes) {
        java.time.YearMonth value = java.time.YearMonth.parse(mes);
        String nombre = value.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return nombre + " " + String.valueOf(value.getYear()).substring(2);
    }

    @Transactional(readOnly = true)
    public ConfiguracionCostosDTO obtenerConfiguracionCostos() {
        return costosVentaService.obtenerConfiguracion();
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> obtenerLibro(String desde, String hasta, String canal, String q) {
        LocalDateTime desdeDate = parseDesde(desde);
        LocalDateTime hastaDate = parseHasta(hasta);
        String qLower = (q != null && !q.isBlank()) ? q.toLowerCase() : null;

        List<VentaResponseDTO> movimientos = new ArrayList<>(obtenerVentasParaExportar(desde, hasta, canal, q).stream()
                .map(v -> mapearADTO(v, envioRepository.findByVentaIdVenta(v.getIdVenta()).orElse(null)))
                .toList());

        if (canal == null || canal.isBlank()) {
            pagoDeudaRepository.findAll().stream()
                    .filter(p -> pagoDentroDeRango(p, desdeDate, hastaDate))
                    .map(this::mapearPagoDeudaADTO)
                    .filter(v -> coincideMovimiento(v, qLower))
                    .forEach(movimientos::add);
        }

        movimientos.sort(Comparator.comparing(VentaResponseDTO::getFechaVenta, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return movimientos;
    }

    @Transactional(readOnly = true)
    public List<com.sonograma.entity.Venta> obtenerVentasParaExportar(String desde, String hasta, String canal, String q) {
        LocalDateTime desdeDate = parseDesde(desde);
        LocalDateTime hastaDate = parseHasta(hasta);
        CanalVenta canalEnum = null;
        if (canal != null && !canal.isBlank()) {
            try { canalEnum = CanalVenta.valueOf(canal); } catch (IllegalArgumentException ignored) {}
        }
        String qLower = (q != null && !q.isBlank()) ? q.toLowerCase() : null;
        final CanalVenta finalCanal = canalEnum;

        return ventaRepository.findAllByOrderByFechaVentaDesc().stream()
                .filter(v -> v.getEstado() != EstadoVenta.CANCELADA)
                .filter(v -> desdeDate == null || !v.getFechaVenta().isBefore(desdeDate))
                .filter(v -> hastaDate == null || !v.getFechaVenta().isAfter(hastaDate))
                .filter(v -> finalCanal == null || finalCanal == v.getCanalVenta())
                .filter(v -> {
                    if (qLower == null) return true;
                    String snapshot = v.getClienteNombreSnapshot() != null ? v.getClienteNombreSnapshot().toLowerCase() : "";
                    String artista = v.getDisco() != null && v.getDisco().getArtista() != null ? v.getDisco().getArtista().toLowerCase()
                            : (!v.getDetalles().isEmpty() && v.getDetalles().get(0).getArtistaSnap() != null ? v.getDetalles().get(0).getArtistaSnap().toLowerCase() : "");
                    String album = v.getDisco() != null && v.getDisco().getAlbum() != null ? v.getDisco().getAlbum().toLowerCase()
                            : (!v.getDetalles().isEmpty() && v.getDetalles().get(0).getAlbumSnap() != null ? v.getDetalles().get(0).getAlbumSnap().toLowerCase() : "");
                    String detalle = v.getDetalles() != null ? v.getDetalles().stream()
                            .map(d -> String.join(" ",
                                    d.getDescripcionSnap() != null ? d.getDescripcionSnap() : "",
                                    d.getCodigoSnap() != null ? d.getCodigoSnap() : ""))
                            .collect(Collectors.joining(" ")).toLowerCase() : "";
                    String factura = v.getNumeroFactura() != null ? v.getNumeroFactura().toLowerCase() : "";
                    return snapshot.contains(qLower) || artista.contains(qLower) || album.contains(qLower)
                            || detalle.contains(qLower) || factura.contains(qLower);
                })
                .collect(Collectors.toList());
    }

    private VentaResponseDTO mapearPagoDeudaADTO(PagoDeuda pago) {
        Deuda deuda = pago.getDeuda();
        Venta venta = deuda != null ? deuda.getVenta() : null;
        Cliente cliente = deuda != null ? deuda.getCliente() : null;
        String nombre = cliente != null
                ? (cliente.getNombre() + (cliente.getApellido() != null ? " " + cliente.getApellido() : "")).trim()
                : (deuda != null ? deuda.getNombreDeudorManual() : null);
        String numeroFactura = deuda != null && deuda.getNumeroFactura() != null
                ? deuda.getNumeroFactura()
                : (venta != null ? venta.getNumeroFactura() : null);
        LocalDateTime fecha = ingresoLibroCalculator.fechaPago(pago);
        return VentaResponseDTO.builder()
                .idVenta(venta != null ? venta.getIdVenta() : null)
                .idPagoDeuda(pago.getIdPagoDeuda())
                .idDeuda(deuda != null ? deuda.getIdDeuda() : null)
                .idCliente(cliente != null ? cliente.getIdCliente() : null)
                .nombreCliente(nombre)
                .fechaVenta(fecha)
                .numeroFactura(numeroFactura)
                .numeroRecibo(pago.getNumeroRecibo())
                .clienteNombreSnapshot(nombre)
                .total(pago.getMonto())
                .totalFinal(pago.getMonto())
                .montoPagado(pago.getMonto())
                .montoDeuda(BigDecimal.ZERO)
                .estadoPago("PAGADO")
                .tipoMovimiento("PAGO_DEUDA")
                .descripcionMovimiento("Pago de deuda")
                .observaciones(pago.getNotas())
                .montoMovimiento(pago.getMonto())
                .detalles(List.of())
                .build();
    }

    private boolean pagoDentroDeRango(PagoDeuda pago, LocalDateTime desde, LocalDateTime hasta) {
        LocalDateTime fecha = ingresoLibroCalculator.fechaPago(pago);
        return (desde == null || !fecha.isBefore(desde))
                && (hasta == null || !fecha.isAfter(hasta));
    }

    private boolean coincideMovimiento(VentaResponseDTO movimiento, String qLower) {
        if (qLower == null) return true;
        return contiene(movimiento.getClienteNombreSnapshot(), qLower)
                || contiene(movimiento.getNombreCliente(), qLower)
                || contiene(movimiento.getNumeroFactura(), qLower)
                || contiene(movimiento.getDescripcionMovimiento(), qLower)
                || contiene(movimiento.getObservaciones(), qLower);
    }

    private VentaResponseDTO mapearADTO(Venta venta, Envio envio) {
        EnvioDTO envioDTO = null;
        if (envio != null) {
            envioDTO = EnvioDTO.builder()
                    .idEnvio(envio.getIdEnvio())
                    .direccionEnvio(envio.getDireccionEnvio())
                    .departamento(envio.getDepartamento())
                    .sucursalDacCodigo(envio.getSucursalDacCodigo())
                    .sucursalDacNombre(envio.getSucursalDacNombre())
                    .costoEnvio(envio.getCostoEnvio())
                    .estadoLogistico(envio.getEstadoLogistico())
                    .numeroSeguimiento(envio.getNumeroSeguimiento())
                    .fechaEnvio(envio.getFechaEnvio())
                    .fechaEntrega(envio.getFechaEntrega())
                    .build();
        }

        List<DetalleVenta> detalles = venta.getDetalles();
        DetalleVenta primerDetalle = (detalles != null && !detalles.isEmpty()) ? detalles.get(0) : null;

        Long idDisco = venta.getDisco() != null ? venta.getDisco().getIdDisco()
                : (primerDetalle != null && primerDetalle.getDisco() != null ? primerDetalle.getDisco().getIdDisco() : null);
        String artista = venta.getDisco() != null ? venta.getDisco().getArtista()
                : (primerDetalle != null ? primerDetalle.getArtistaSnap() : null);
        String album = venta.getDisco() != null ? venta.getDisco().getAlbum()
                : (primerDetalle != null ? primerDetalle.getAlbumSnap() : null);

        List<DetalleVentaResponseDTO> detallesDTO = detalles != null ? detalles.stream()
                .map(d -> DetalleVentaResponseDTO.builder()
                        .idDetalle(d.getIdDetalle())
                        .idDisco(d.getDisco() != null ? d.getDisco().getIdDisco() : null)
                        .artista(d.getArtistaSnap() != null ? d.getArtistaSnap() : (d.getDisco() != null ? d.getDisco().getArtista() : null))
                        .album(d.getAlbumSnap() != null ? d.getAlbumSnap() : (d.getDisco() != null ? d.getDisco().getAlbum() : null))
                        .descripcion(d.getDescripcionSnap())
                        .codigoInterno(d.getCodigoSnap() != null ? d.getCodigoSnap() : (d.getDisco() != null ? d.getDisco().getCodigoInterno() : null))
                        .imagenUrl(d.getDisco() != null ? d.getDisco().getImagenUrl() : null)
                        .cantidad(cantidadDetalle(d))
                        .precioUnitario(d.getPrecioUnitario())
                        .manualItem(Boolean.TRUE.equals(d.getManualItem()) || d.getDisco() == null)
                        .build())
                .collect(Collectors.toList()) : List.of();

        return VentaResponseDTO.builder()
                .idVenta(venta.getIdVenta())
                .idCliente(venta.getCliente().getIdCliente())
                .nombreCliente(venta.getCliente().getNombre())
                .apellidoCliente(venta.getCliente().getApellido())
                .idDisco(idDisco)
                .artista(artista)
                .album(album)
                .fechaVenta(venta.getFechaVenta())
                .canalVenta(venta.getCanalVenta() != null ? venta.getCanalVenta().name() : null)
                .total(VentaTotals.totalProductos(venta))
                .costoDisco(venta.getCostoDisco())
                .precioVenta(venta.getPrecioVenta())
                .costoEnvio(venta.getCostoEnvio())
                .porcentajeImpuesto(venta.getPorcentajeImpuesto())
                .montoImpuesto(venta.getMontoImpuesto())
                .otrosCostos(venta.getOtrosCostos())
                .totalFinal(VentaTotals.totalProductos(venta))
                .gananciaEstimada(venta.getGananciaEstimada())
                .tipoEntrega(venta.getTipoEntrega() != null ? venta.getTipoEntrega().name() : null)
                .estado(venta.getEstado() != null ? venta.getEstado().name() : null)
                .observaciones(venta.getObservaciones())
                .envio(envioDTO)
                .numeroFactura(venta.getNumeroFactura())
                .numeroRecibo(venta.getNumeroRecibo())
                .clienteNombreSnapshot(venta.getClienteNombreSnapshot())
                .medioPago(venta.getMedioPago() != null ? venta.getMedioPago().name() : null)
                .montoPagado(venta.getMontoPagado())
                .montoDeuda(venta.getMontoDeuda())
                .estadoPago(venta.getEstadoPago() != null ? venta.getEstadoPago().name() : null)
                .subtotal(venta.getSubtotal())
                .descuentoPorcentaje(venta.getDescuentoPorcentaje())
                .detalles(detallesDTO)
                .tipoMovimiento("PRE_VENTA".equals(venta.getOrigen()) ? "PRE_VENTA" : "VENTA")
                .descripcionMovimiento("PRE_VENTA".equals(venta.getOrigen()) ? "Cobro de pre-venta" : "Venta")
                .montoMovimiento(ingresoLibroCalculator.montoVenta(venta))
                .origen(venta.getOrigen())
                .idPreVentaOrigen(venta.getIdPreVentaOrigen())
                .build();
    }

    private String generarNumeroFactura(int anio) {
        long count = ventaRepository.countByAnio(anio) + 1;
        return String.format("F-%d-%03d", anio, count);
    }

    private PreparedDetalle prepararDetalle(DetalleVentaDTO dto) {
        if (dto == null) {
            throw new NegocioException("Detalle de venta inválido");
        }
        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 1;
        if (cantidad <= 0) {
            throw new NegocioException("La cantidad de cada ítem debe ser positiva");
        }
        BigDecimal precio = dto.getPrecioUnitario() != null ? dto.getPrecioUnitario() : BigDecimal.ZERO;
        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("Ingresá un precio unitario válido para cada ítem");
        }

        if (dto.getIdDisco() != null) {
            Disco disco = discoRepository.findById(dto.getIdDisco())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Disco", dto.getIdDisco()));
            discoQrCopyService.synchronize(disco);
            validarStockDisponible(disco, cantidad);
            return new PreparedDetalle(
                    disco,
                    precio,
                    cantidad,
                    false,
                    disco.getArtista(),
                    disco.getAlbum(),
                    descripcionDetalle(dto, disco),
                    disco.getCodigoInterno(),
                    (disco.getCosto() != null ? disco.getCosto() : BigDecimal.ZERO)
                            .multiply(BigDecimal.valueOf(cantidad)),
                    dto.getCopyId(),
                    dto.getCodigoQr()
            );
        }

        String descripcion = firstNonBlank(dto.getDescripcion(), descripcionManual(dto));
        if (descripcion == null) {
            throw new NegocioException("Ingresá una descripción para el disco fuera de catálogo");
        }
        return new PreparedDetalle(
                null,
                precio,
                cantidad,
                true,
                textoNulo(dto.getArtista()),
                textoNulo(dto.getAlbum()),
                descripcion,
                textoNulo(dto.getCodigo()),
                BigDecimal.ZERO,
                null,
                null
        );
    }

    private DetalleVenta detalleDesdePreparado(Venta venta, PreparedDetalle preparado, String copyIdsSnapshot) {
        return DetalleVenta.builder()
                .venta(venta)
                .disco(preparado.disco())
                .precioUnitario(preparado.precioUnitario())
                .cantidad(preparado.cantidad())
                .manualItem(preparado.manualItem())
                .artistaSnap(preparado.artistaSnap())
                .albumSnap(preparado.albumSnap())
                .descripcionSnap(preparado.descripcionSnap())
                .codigoSnap(preparado.codigoSnap())
                .copyIdsSnapshot(copyIdsSnapshot)
                .build();
    }

    private void validarStockDisponible(Disco disco) {
        validarStockDisponible(disco, 1);
    }

    private void validarStockDisponible(Disco disco, int cantidad) {
        int copias = (int) discoQrCopyService.countAvailableCopies(disco.getIdDisco());
        if (copias < cantidad || disco.getEstado() == EstadoDisco.SIN_STOCK) {
            throw new NegocioException("El disco '" + disco.getArtista() + " – " + disco.getAlbum() + "' está sin stock");
        }
    }

    private String reservarStock(PreparedDetalle preparado) {
        if (preparado.disco() == null) return null;
        List<DiscoQrCopy> reserved = discoQrCopyService.reserveCopies(
                preparado.disco(),
                Math.max(1, preparado.cantidad()),
                preparado.copyId(),
                preparado.codigoQr()
        );
        long restantes = discoQrCopyService.countAvailableCopies(preparado.disco().getIdDisco());
        preparado.disco().setCantidadCopias((int) restantes);
        discoEstadoService.aplicar(preparado.disco());
        discoRepository.save(preparado.disco());
        return reserved.stream().map(copy -> String.valueOf(copy.getId())).collect(Collectors.joining(","));
    }

    private void restaurarStockVenta(Venta venta) {
        if (venta.getDetalles() != null && !venta.getDetalles().isEmpty()) {
            venta.getDetalles().stream()
                    .filter(d -> d.getDisco() != null)
                    .forEach(this::restaurarStock);
        } else if (venta.getDisco() != null) {
            restaurarStock(venta.getDisco(), 1);
        }
    }

    private void restaurarStock(Disco disco, int cantidad) {
        int copias = disco.getCantidadCopias() != null ? disco.getCantidadCopias() : 0;
        disco.setCantidadCopias(copias + Math.max(1, cantidad));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        discoRepository.save(disco);
    }

    private void restaurarStock(DetalleVenta detalle) {
        if (detalle.getDisco() == null) return;
        if (detalle.getCopyIdsSnapshot() == null || detalle.getCopyIdsSnapshot().isBlank()) {
            restaurarStock(detalle.getDisco(), cantidadDetalle(detalle));
            return;
        }
        discoQrCopyService.restoreCopies(detalle.getCopyIdsSnapshot());
        discoEstadoService.aplicar(detalle.getDisco());
        discoRepository.save(detalle.getDisco());
    }

    private BigDecimal calcularMontoPagado(VentaRequestDTO dto, BigDecimal totalProductos) {
        BigDecimal total = totalProductos != null ? totalProductos : BigDecimal.ZERO;
        BigDecimal montoPagado = dto.getMontoPagado() != null ? dto.getMontoPagado() : total;
        if (montoPagado.compareTo(total) > 0) {
            throw new NegocioException("El monto pagado no puede superar el total de la venta");
        }
        return montoPagado;
    }

    private Envio sincronizarEnvio(
            Venta venta,
            Cliente cliente,
            VentaRequestDTO dto,
            TipoEntrega entrega,
            ResultadoCostoVentaDTO costos) {
        Envio envio = envioRepository.findByVentaIdVenta(venta.getIdVenta()).orElse(null);
        if (entrega != TipoEntrega.ENVIO) {
            if (envio != null) {
                envioRepository.delete(envio);
            }
            return null;
        }
        DireccionCliente direccionCliente = resolverDireccionCliente(cliente, dto);
        String direccionCompleta = armarDireccionEnvio(direccionCliente, dto);
        if (envio == null) {
            envio = Envio.builder()
                    .venta(venta)
                    .estadoLogistico("PREPARANDO")
                    .build();
        }
        envio.setDireccionEnvio(direccionCompleta);
        envio.setDepartamento(dto.getDepartamento());
        envio.setSucursalDacCodigo(dto.getSucursalDacCodigo());
        envio.setSucursalDacNombre(dto.getSucursalDacNombre());
        envio.setCostoEnvio(costos.getCostoEnvio());
        return envioRepository.save(envio);
    }

    private DireccionCliente resolverDireccionCliente(Cliente cliente, VentaRequestDTO dto) {
        if (dto.getDepartamento() == null || dto.getDepartamento().isBlank()) {
            throw new NegocioException("Seleccioná el departamento del envío");
        }

        if (dto.getIdDireccionCliente() != null) {
            DireccionCliente direccion = direccionClienteRepository.findById(dto.getIdDireccionCliente())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Dirección", dto.getIdDireccionCliente()));
            if (!direccion.getCliente().getIdCliente().equals(cliente.getIdCliente())) {
                throw new NegocioException("La dirección seleccionada no pertenece al cliente");
            }
            direccion.setUltimaUsada(LocalDateTime.now());
            return direccionClienteRepository.save(direccion);
        }

        String direccion = dto.getDireccionEnvio() != null ? dto.getDireccionEnvio().trim() : "";
        if (direccion.isBlank()) {
            return null;
        }
        return clienteService.guardarDireccion(cliente, direccion, dto.getDepartamento(), null, true);
    }

    private String armarDireccionEnvio(DireccionCliente direccionCliente, VentaRequestDTO dto) {
        String direccion = direccionCliente != null ? direccionCliente.getDireccion() : dto.getDireccionEnvio();
        String departamento = dto.getDepartamento() != null ? dto.getDepartamento().trim() : null;
        String sucursal = dto.getSucursalDacNombre() != null ? dto.getSucursalDacNombre().trim() : null;

        StringBuilder sb = new StringBuilder();
        if (direccion != null && !direccion.isBlank()) {
            sb.append(direccion.trim());
        }
        if (departamento != null && !departamento.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(departamento);
        }
        if (sucursal != null && !sucursal.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append("DAC ").append(sucursal);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private LocalDateTime parseDesde(String desde) {
        return desde != null && !desde.isBlank()
                ? LocalDateTime.parse(desde + "T00:00:00") : null;
    }

    private LocalDateTime parseHasta(String hasta) {
        return hasta != null && !hasta.isBlank()
                ? LocalDate.parse(hasta).atTime(LocalTime.MAX) : null;
    }

    private int cantidadDetalle(DetalleVenta detalle) {
        return detalle.getCantidad() != null && detalle.getCantidad() > 0 ? detalle.getCantidad() : 1;
    }

    private String descripcionDetalle(DetalleVentaDTO dto, Disco disco) {
        return firstNonBlank(dto.getDescripcion(), disco.getArtista() + " - " + disco.getAlbum());
    }

    private String descripcionManual(DetalleVentaDTO dto) {
        return java.util.stream.Stream.of(dto.getArtista(), dto.getAlbum(), dto.getCodigo())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" - "));
    }

    private String textoNulo(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean contiene(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private record PreparedDetalle(
            Disco disco,
            BigDecimal precioUnitario,
            int cantidad,
            boolean manualItem,
            String artistaSnap,
            String albumSnap,
            String descripcionSnap,
            String codigoSnap,
            BigDecimal costoTotal,
            Long copyId,
            String codigoQr
    ) {}

    private CanalVenta parseCanal(String canalVenta) {
        try {
            return CanalVenta.valueOf(canalVenta);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new NegocioException("Canal de venta inválido: " + canalVenta);
        }
    }

    private TipoEntrega parseEntrega(String tipoEntrega) {
        try {
            return TipoEntrega.valueOf(tipoEntrega);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new NegocioException("Tipo de entrega inválido: " + tipoEntrega);
        }
    }

    private MedioPago parseMedioPago(String medioPago) {
        if (medioPago == null || medioPago.isBlank()) return null;
        try {
            return MedioPago.valueOf(medioPago);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException("Método de pago inválido: " + medioPago);
        }
    }
}
