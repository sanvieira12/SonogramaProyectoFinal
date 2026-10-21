package com.sonograma.service;

import com.sonograma.dto.*;
import com.sonograma.entity.*;
import com.sonograma.enums.*;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PreVentaService {

    private final PreVentaRepository repository;
    private final PreVentaTransaccionRepository transaccionRepository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final EnvioRepository envioRepository;
    private final DeudaRepository deudaRepository;
    private final ProfitCalculationService profitCalculationService;
    private final BusinessTime businessTime;

    @Transactional(readOnly = true)
    public List<PreVentaResponseDTO> listar() {
        return repository.findAllByOrderByFechaDescIdPreVentaDesc().stream().map(this::toDto).toList();
    }

    /** Backward-compatible single-item creation. Every new row still receives a parent. */
    public PreVentaResponseDTO crear(PreVentaRequestDTO request) {
        validarCantidadPrecio(request.getCantidad(), request.getPrecio());
        Cliente cliente = clienteActivo(request.getIdCliente());
        LocalDate fecha = request.getFecha() != null ? request.getFecha() : businessTime.today();
        PreVentaTransaccion transaccion = transaccionRepository.save(PreVentaTransaccion.builder()
            .cliente(cliente)
            .fecha(fecha)
            .notas(textoNulo(request.getNotas()))
            .idempotencyKey("legacy-api-" + UUID.randomUUID())
            .build());

        PreVenta item = construirItem(transaccion, cliente, fecha, request.getIdDisco(),
            request.getDescripcion(), request.getCodigoDisco(), request.getCantidad(),
            request.getPrecio(), request.getNotas());
        item = repository.save(item);
        transaccion.getItems().add(item);
        return toDto(item);
    }

    @Transactional(readOnly = true)
    public List<PreVentaTransaccionResponseDTO> listarTransacciones() {
        return transaccionRepository.findAllByOrderByFechaDescIdPreVentaTransaccionDesc().stream()
            .map(this::toTransaccionDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public PreVentaTransaccionResponseDTO obtenerTransaccion(Long id) {
        return toTransaccionDto(transaccionRepository.findDetailedById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Transacción de pre-venta", id)));
    }

    public PreVentaTransaccionResponseDTO crearTransaccion(PreVentaTransaccionRequestDTO request) {
        String idempotencyKey = request.getIdempotencyKey().trim();
        String fingerprint = fingerprint(request);
        Cliente cliente = clienteActivoBloqueado(request.getIdCliente());
        var existente = transaccionRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            if (!Objects.equals(fingerprint, existente.get().getRequestFingerprint())) {
                throw new ConflictoNegocioException(
                    "La clave de idempotencia ya fue usada con una pre-venta diferente");
            }
            return obtenerTransaccion(existente.get().getIdPreVentaTransaccion());
        }

        LocalDate fecha = request.getFecha() != null ? request.getFecha() : businessTime.today();
        PreVentaTransaccion transaccion = transaccionRepository.save(PreVentaTransaccion.builder()
            .cliente(cliente)
            .fecha(fecha)
            .notas(textoNulo(request.getNotas()))
            .idempotencyKey(idempotencyKey)
            .requestFingerprint(fingerprint)
            .build());

        for (PreVentaTransaccionItemRequestDTO itemRequest : request.getItems()) {
            validarCantidadPrecio(itemRequest.getCantidad(), itemRequest.getPrecio());
            PreVenta item = construirItem(transaccion, cliente, fecha, itemRequest.getIdDisco(),
                itemRequest.getDescripcion(), itemRequest.getCodigoDisco(), itemRequest.getCantidad(),
                itemRequest.getPrecio(), itemRequest.getNotas());
            transaccion.getItems().add(repository.save(item));
        }
        return toTransaccionDto(transaccion);
    }

    public PreVentaTransaccionResponseDTO agregarItem(
            Long transaccionId, PreVentaTransaccionItemRequestDTO request) {
        validarCantidadPrecio(request.getCantidad(), request.getPrecio());
        String operationKey = textoNulo(request.getIdempotencyKey());
        if (operationKey == null) {
            throw new NegocioException("La clave de idempotencia del ítem es obligatoria");
        }
        PreVentaTransaccion transaccion = lockTransaccion(transaccionId);
        List<PreVenta> items = new ArrayList<>(repository.findAllByTransaccionIdForUpdate(transaccionId));
        var existente = items.stream()
            .filter(item -> operationKey.equals(item.getOperationKey()))
            .findFirst();
        if (existente.isPresent()) {
            if (!coincideSolicitud(existente.get(), request)) {
                throw new ConflictoNegocioException(
                    "La clave de idempotencia ya fue usada con un ítem diferente");
            }
            return toTransaccionDto(transaccion, items);
        }
        Cliente cliente = transaccion.getCliente();
        PreVenta item = construirItem(transaccion, cliente, transaccion.getFecha(), request.getIdDisco(),
            request.getDescripcion(), request.getCodigoDisco(), request.getCantidad(),
            request.getPrecio(), request.getNotas());
        item.setOperationKey(operationKey);
        item = repository.save(item);
        items.add(item);
        return toTransaccionDto(transaccion, items);
    }

    public PreVentaTransaccionResponseDTO actualizarItem(
            Long transaccionId, Long itemId, PreVentaItemUpdateRequestDTO request) {
        validarCantidadPrecio(request.getCantidad(), request.getPrecio());
        PreVentaTransaccion transaccion = lockTransaccion(transaccionId);
        List<PreVenta> items = repository.findAllByTransaccionIdForUpdate(transaccionId);
        PreVenta item = itemDeTransaccion(items, itemId, transaccionId);
        asegurarNoPagada(item);
        item.setCantidad(request.getCantidad());
        item.setPrecio(request.getPrecio().setScale(2, RoundingMode.HALF_UP));
        item.setNotas(textoNulo(request.getNotas()));
        repository.save(item);
        return toTransaccionDto(transaccion, items);
    }

    public PreVentaTransaccionResponseDTO eliminarItem(Long transaccionId, Long itemId) {
        PreVentaTransaccion transaccion = lockTransaccion(transaccionId);
        List<PreVenta> items = new ArrayList<>(repository.findAllByTransaccionIdForUpdate(transaccionId));
        PreVenta item = itemDeTransaccion(items, itemId, transaccionId);
        asegurarNoPagada(item);
        if (items.size() == 1) {
            throw new NegocioException("Una transacción de pre-venta debe conservar al menos un ítem");
        }
        repository.delete(item);
        repository.flush();
        items.remove(item);
        return toTransaccionDto(transaccion, items);
    }

    public PreVentaTransaccionResponseDTO pagarPendientes(Long transaccionId) {
        PreVentaTransaccion transaccion = lockTransaccion(transaccionId);
        List<PreVenta> items = repository.findAllByTransaccionIdForUpdate(transaccionId);
        if (items.isEmpty()) {
            throw new ConflictoNegocioException("La transacción no contiene ítems");
        }
        validarClientes(transaccion, items);
        for (PreVenta item : items) {
            if (estaPagada(item)) continue;
            if (item.getVentaPago() != null || "PAGADA".equals(item.getEstado())) {
                throw new ConflictoNegocioException(
                    "El estado financiero del ítem #" + item.getIdPreVenta() + " es inconsistente");
            }
            marcarPagadaBloqueada(item);
        }
        return toTransaccionDto(transaccion, items);
    }

    public VentaResponseDTO actualizarPago(Long id, PreVentaPagoUpdateRequestDTO request) {
        PreVenta preVenta = lockItem(id);
        Venta venta = validarPago(preVenta, id);
        validarSinMovimientosRelacionados(venta);
        DetalleVenta detalle = detalleUnico(venta);
        BigDecimal precio = request.getPrecio().setScale(2, RoundingMode.HALF_UP);
        int cantidad = request.getCantidad();
        BigDecimal precioUnitario = precio.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);

        preVenta.setPrecio(precio);
        preVenta.setCantidad(cantidad);
        preVenta.setFechaPago(request.getFechaPago());
        preVenta.setEstado("PAGADA");
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(precioUnitario);

        venta.setFechaVenta(request.getFechaPago());
        venta.setTotal(precio);
        venta.setPrecioVenta(precio);
        venta.setSubtotal(precio);
        venta.setTotalFinal(precio);
        venta.setMontoPagado(precio);
        venta.setMontoDeuda(BigDecimal.ZERO);
        venta.setEstadoPago(EstadoPago.PAGADO);
        venta.setMedioPago(parseMedioPago(request.getMedioPago()));
        venta.setNumeroRecibo(textoNulo(request.getNumeroRecibo()));
        venta.setObservaciones(textoNulo(request.getObservaciones()));
        venta.setCostoDisco(costoHistorico(preVenta.getDisco(), cantidad));
        venta.setGananciaEstimada(profitCalculationService.netProfitForSale(venta).netProfit());
        ventaRepository.save(venta);
        detalleVentaRepository.save(detalle);
        repository.save(preVenta);
        return mapearVenta(venta);
    }

    public void eliminarPago(Long id) {
        PreVenta preVenta = lockItem(id);
        Venta venta = validarPago(preVenta, id);
        validarSinMovimientosRelacionados(venta);
        preVenta.setVentaPago(null);
        preVenta.setFechaPago(null);
        preVenta.setEstado("PENDIENTE");
        venta.setIdPreVentaOrigen(null);
        repository.saveAndFlush(preVenta);
        ventaRepository.saveAndFlush(venta);
        List<DetalleVenta> detalles = new ArrayList<>(venta.getDetalles());
        if (!detalles.isEmpty()) {
            detalleVentaRepository.deleteAll(detalles);
            detalleVentaRepository.flush();
        }
        ventaRepository.delete(venta);
        ventaRepository.flush();
    }

    public PreVentaResponseDTO marcarPagada(Long id) {
        return toDto(marcarPagadaBloqueada(lockItem(id)));
    }

    public void eliminar(Long id) {
        PreVenta preVenta = lockItem(id);
        asegurarNoPagada(preVenta);
        PreVentaTransaccion transaccion = preVenta.getTransaccion();
        repository.delete(preVenta);
        if (transaccion != null) {
            repository.flush();
            transaccionRepository.deleteIfEmpty(transaccion.getIdPreVentaTransaccion());
        }
    }

    private PreVenta marcarPagadaBloqueada(PreVenta preVenta) {
        if ("PAGADA".equals(preVenta.getEstado()) || preVenta.getVentaPago() != null) {
            throw new NegocioException("La pre-venta ya fue marcada como pagada");
        }
        validarCantidadPrecio(preVenta.getCantidad(), preVenta.getPrecio());
        int cantidad = preVenta.getCantidad();
        LocalDateTime fechaPago = businessTime.now();
        Venta venta = Venta.builder()
            .cliente(preVenta.getCliente()).fechaVenta(fechaPago).canalVenta(CanalVenta.LOCAL)
            .total(preVenta.getPrecio()).precioVenta(preVenta.getPrecio()).totalFinal(preVenta.getPrecio())
            .costoDisco(costoHistorico(preVenta.getDisco(), cantidad))
            .subtotal(preVenta.getPrecio()).descuentoPorcentaje(BigDecimal.ZERO)
            .costoEnvio(BigDecimal.ZERO).otrosCostos(BigDecimal.ZERO).montoImpuesto(BigDecimal.ZERO)
            .gananciaEstimada(null).tipoEntrega(TipoEntrega.RETIRO)
            .estado(EstadoVenta.COMPLETADA).observaciones("Cobro de pre-venta #" + preVenta.getIdPreVenta())
            .numeroFactura("PV-" + preVenta.getIdPreVenta()).clienteNombreSnapshot(nombreCliente(preVenta.getCliente()))
            .medioPago(MedioPago.OTRO).montoPagado(preVenta.getPrecio()).montoDeuda(BigDecimal.ZERO)
            .estadoPago(EstadoPago.PAGADO).origen("PRE_VENTA")
            .idPreVentaOrigen(preVenta.getIdPreVenta()).build();
        venta = ventaRepository.saveAndFlush(venta);

        BigDecimal unitario = preVenta.getPrecio().divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
        AcquisitionCostResolution acquisition = preVenta.getDisco() != null
            ? profitCalculationService.acquisitionCostForDisco(preVenta.getDisco()) : null;
        DetalleVenta detalle = DetalleVenta.builder().venta(venta).disco(preVenta.getDisco())
            .precioUnitario(unitario).cantidad(cantidad).manualItem(preVenta.getDisco() == null)
            .costoAdquisicionUnitario(acquisition != null ? acquisition.originalAmount() : null)
            .costoAdquisicionUnitarioUyu(acquisition != null ? acquisition.unitCostUyu() : null)
            .costoAdquisicionMonedaOriginal(acquisition != null ? acquisition.originalCurrency() : null)
            .tipoCambioAdquisicion(acquisition != null ? acquisition.exchangeRateUsed() : null)
            .costoAdquisicionFuente(acquisition != null ? acquisition.source() : null)
            .artistaSnap(preVenta.getDisco() != null ? preVenta.getDisco().getArtista() : preVenta.getArtistaSnap())
            .albumSnap(preVenta.getDisco() != null ? preVenta.getDisco().getAlbum() : preVenta.getAlbumSnap())
            .descripcionSnap(preVenta.getDescripcionSnap()).codigoSnap(preVenta.getCodigoDisco())
            .clasificacionItem(clasificacionDesdeCatalogo(preVenta.getDisco())).build();
        venta.getDetalles().add(detalleVentaRepository.save(detalle));
        preVenta.setEstado("PAGADA");
        preVenta.setFechaPago(fechaPago);
        preVenta.setVentaPago(venta);
        return repository.save(preVenta);
    }

    private PreVenta construirItem(PreVentaTransaccion transaccion, Cliente cliente, LocalDate fecha,
            Long idDisco, String descripcionSolicitada, String codigoSolicitado,
            Integer cantidad, BigDecimal precio, String notas) {
        Disco disco = idDisco != null
            ? discoRepository.findById(idDisco)
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", idDisco))
            : null;
        String descripcion = disco == null ? textoNulo(descripcionSolicitada) : null;
        if (disco == null && descripcion == null) {
            throw new NegocioException("Seleccioná un disco o ingresá una descripción para la pre-venta");
        }
        String codigo = normalizarCodigo(disco != null ? disco.getCodigoInterno() : codigoSolicitado);
        return PreVenta.builder()
            .transaccion(transaccion).cliente(cliente).disco(disco).fecha(fecha)
            .cantidad(cantidad).precio(precio.setScale(2, RoundingMode.HALF_UP)).estado("PENDIENTE")
            .codigoDisco(codigo).codigoDiscoNormalizado(PreVentaCodeMatcher.normalize(codigo))
            .notas(textoNulo(notas))
            .artistaSnap(disco != null ? disco.getArtista() : null)
            .albumSnap(disco != null ? disco.getAlbum() : null)
            .descripcionSnap(descripcion)
            .build();
    }

    private PreVenta lockItem(Long id) {
        repository.findTransactionIdByItemId(id).ifPresent(this::lockTransaccion);
        return repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pre-venta", id));
    }

    private PreVentaTransaccion lockTransaccion(Long id) {
        return transaccionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Transacción de pre-venta", id));
    }

    private PreVenta itemDeTransaccion(List<PreVenta> items, Long itemId, Long transaccionId) {
        return items.stream().filter(item -> Objects.equals(item.getIdPreVenta(), itemId)).findFirst()
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "Ítem de pre-venta de la transacción " + transaccionId, itemId));
    }

    private void validarCantidadPrecio(Integer cantidad, BigDecimal precio) {
        if (cantidad == null || cantidad <= 0) throw new NegocioException("La cantidad debe ser positiva");
        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("La pre-venta no tiene un importe válido");
        }
    }

    private void asegurarNoPagada(PreVenta item) {
        if ("PAGADA".equals(item.getEstado()) || item.getVentaPago() != null) {
            throw new NegocioException("No se puede modificar un ítem de pre-venta pagado");
        }
    }

    private boolean estaPagada(PreVenta item) {
        return "PAGADA".equals(item.getEstado()) && item.getVentaPago() != null;
    }

    private void validarClientes(PreVentaTransaccion transaccion, List<PreVenta> items) {
        Long clienteId = transaccion.getCliente().getIdCliente();
        if (items.stream().anyMatch(item -> !Objects.equals(clienteId, item.getCliente().getIdCliente()))) {
            throw new ConflictoNegocioException("La transacción contiene ítems de otro cliente");
        }
    }

    private Cliente clienteActivo(Long id) {
        return clienteRepository.findById(id).filter(Cliente::getActivo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private Cliente clienteActivoBloqueado(Long id) {
        return clienteRepository.findByIdForUpdate(id).filter(Cliente::getActivo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private boolean coincideSolicitud(PreVenta item, PreVentaTransaccionItemRequestDTO request) {
        Long existingDiscoId = item.getDisco() != null ? item.getDisco().getIdDisco() : null;
        return Objects.equals(existingDiscoId, request.getIdDisco())
            && Objects.equals(item.getCantidad(), request.getCantidad())
            && item.getPrecio().compareTo(request.getPrecio().setScale(2, RoundingMode.HALF_UP)) == 0
            && Objects.equals(item.getNotas(), textoNulo(request.getNotas()))
            && (request.getIdDisco() != null || (
                Objects.equals(item.getDescripcionSnap(), textoNulo(request.getDescripcion()))
                && Objects.equals(item.getCodigoDisco(), normalizarCodigo(request.getCodigoDisco()))));
    }

    private Venta validarPago(PreVenta preVenta, Long id) {
        if (!estaPagada(preVenta)) {
            throw new ConflictoNegocioException("La pre-venta no tiene un cobro pagado asociado");
        }
        Venta venta = preVenta.getVentaPago();
        if (!"PRE_VENTA".equals(venta.getOrigen()) || !Objects.equals(id, venta.getIdPreVentaOrigen())) {
            throw new ConflictoNegocioException("El cobro no corresponde a esta pre-venta");
        }
        if (venta.getEstado() == EstadoVenta.CANCELADA) {
            throw new ConflictoNegocioException("El cobro de la pre-venta ya fue cancelado");
        }
        return venta;
    }

    private DetalleVenta detalleUnico(Venta venta) {
        if (venta.getDetalles() == null || venta.getDetalles().size() != 1) {
            throw new ConflictoNegocioException("El cobro de la pre-venta no tiene un único detalle válido");
        }
        return venta.getDetalles().get(0);
    }

    private void validarSinMovimientosRelacionados(Venta venta) {
        Long idVenta = venta.getIdVenta();
        if (envioRepository.findByVentaIdVenta(idVenta).isPresent()
                || deudaRepository.findByVentaIdVenta(idVenta).isPresent()) {
            throw new ConflictoNegocioException(
                "El cobro de la pre-venta tiene movimientos relacionados no soportados");
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

    private String textoNulo(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizarCodigo(String value) {
        String normalized = textoNulo(value);
        return normalized == null ? null : normalized.replaceAll("\\s+", " ");
    }

    private String nombreCliente(Cliente cliente) {
        return (cliente.getNombre() + " "
            + (cliente.getApellido() != null ? cliente.getApellido() : "")).trim();
    }

    private BigDecimal costoHistorico(Disco disco, int cantidad) {
        if (disco == null) return null;
        AcquisitionCostResolution acquisition = profitCalculationService.acquisitionCostForDisco(disco);
        return acquisition.isComplete()
            ? acquisition.unitCostUyu().multiply(BigDecimal.valueOf(cantidad)) : null;
    }

    private ClasificacionItemVenta clasificacionDesdeCatalogo(Disco disco) {
        if (disco == null || disco.getCondicion() == null) return null;
        return switch (disco.getCondicion()) {
            case NUEVO -> ClasificacionItemVenta.NUEVO;
            case USADO -> ClasificacionItemVenta.USADO;
            case CONSIGNACION, CATALOGO -> null;
        };
    }

    private PreVentaTransaccionResponseDTO toTransaccionDto(PreVentaTransaccion transaccion) {
        return toTransaccionDto(transaccion, transaccion.getItems());
    }

    private PreVentaTransaccionResponseDTO toTransaccionDto(
            PreVentaTransaccion transaccion, List<PreVenta> items) {
        validarClientes(transaccion, items);
        BigDecimal total = items.stream().map(PreVenta::getPrecio).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = items.stream().filter(this::estaPagada).map(PreVenta::getPrecio)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendiente = total.subtract(pagado);
        String estado = pagado.signum() == 0 ? "PENDIENTE"
            : pendiente.signum() == 0 ? "PAGADA" : "PARCIALMENTE_PAGADA";
        int unidades = items.stream().map(PreVenta::getCantidad).filter(Objects::nonNull)
            .reduce(0, Integer::sum);
        return PreVentaTransaccionResponseDTO.builder()
            .idPreVentaTransaccion(transaccion.getIdPreVentaTransaccion())
            .idCliente(transaccion.getCliente().getIdCliente())
            .clienteNombre(nombreCliente(transaccion.getCliente()))
            .fecha(transaccion.getFecha()).notas(transaccion.getNotas())
            .items(items.stream().map(this::toDto).toList())
            .cantidadItems(items.size()).cantidadUnidades(unidades)
            .total(total).pagado(pagado).pendiente(pendiente).estadoPago(estado)
            .build();
    }

    private PreVentaResponseDTO toDto(PreVenta preVenta) {
        Cliente cliente = preVenta.getCliente();
        Disco disco = preVenta.getDisco();
        return PreVentaResponseDTO.builder()
            .idPreVenta(preVenta.getIdPreVenta()).idCliente(cliente.getIdCliente())
            .clienteNombre(nombreCliente(cliente)).idDisco(disco != null ? disco.getIdDisco() : null)
            .artista(disco != null ? disco.getArtista() : preVenta.getArtistaSnap())
            .album(disco != null ? disco.getAlbum() : preVenta.getAlbumSnap())
            .descripcion(preVenta.getDescripcionSnap()).codigoDisco(preVenta.getCodigoDisco())
            .idVentaPago(preVenta.getVentaPago() != null ? preVenta.getVentaPago().getIdVenta() : null)
            .fechaPago(preVenta.getFechaPago()).cantidad(preVenta.getCantidad())
            .precio(preVenta.getPrecio()).fecha(preVenta.getFecha())
            .estado(preVenta.getEstado()).notas(preVenta.getNotas()).build();
    }

    private VentaResponseDTO mapearVenta(Venta venta) {
        return VentaResponseDTO.builder()
            .idVenta(venta.getIdVenta()).idCliente(venta.getCliente().getIdCliente())
            .fechaVenta(venta.getFechaVenta())
            .medioPago(venta.getMedioPago() != null ? venta.getMedioPago().name() : null)
            .numeroRecibo(venta.getNumeroRecibo()).montoPagado(venta.getMontoPagado())
            .montoDeuda(venta.getMontoDeuda())
            .estadoPago(venta.getEstadoPago() != null ? venta.getEstadoPago().name() : null)
            .totalFinal(venta.getTotalFinal()).tipoMovimiento("PRE_VENTA")
            .descripcionMovimiento("Cobro de pre-venta").montoMovimiento(venta.getMontoPagado())
            .origen(venta.getOrigen()).idPreVentaOrigen(venta.getIdPreVentaOrigen())
            .observaciones(venta.getObservaciones()).build();
    }

    private String fingerprint(PreVentaTransaccionRequestDTO request) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, request.getIdCliente());
        appendField(canonical, request.getFecha());
        appendField(canonical, textoNulo(request.getNotas()));
        for (PreVentaTransaccionItemRequestDTO item : request.getItems()) {
            appendField(canonical, item.getIdDisco());
            appendField(canonical, textoNulo(item.getDescripcion()));
            appendField(canonical, normalizarCodigo(item.getCodigoDisco()));
            appendField(canonical, item.getCantidad());
            appendField(canonical, item.getPrecio() != null
                ? item.getPrecio().stripTrailingZeros().toPlainString() : null);
            appendField(canonical, textoNulo(item.getNotas()));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private void appendField(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }
}
