package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaResponseDTO;
import com.sonograma.dto.PreVentaPagoUpdateRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PreVenta;
import com.sonograma.entity.Venta;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.enums.*;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.PreVentaRepository;
import com.sonograma.repository.VentaRepository;
import com.sonograma.repository.DetalleVentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class PreVentaService {

    private final PreVentaRepository repository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final EnvioRepository envioRepository;
    private final DeudaRepository deudaRepository;
    private final ProfitCalculationService profitCalculationService;

    @Transactional(readOnly = true)
    public List<PreVentaResponseDTO> listar() {
        return repository.findAllByOrderByFechaDescIdPreVentaDesc().stream().map(this::toDto).toList();
    }

    public PreVentaResponseDTO crear(PreVentaRequestDTO request) {
        Cliente cliente = clienteRepository.findById(request.getIdCliente())
            .filter(Cliente::getActivo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.getIdCliente()));
        Disco disco = request.getIdDisco() != null
            ? discoRepository.findById(request.getIdDisco()).orElseThrow(() -> new RecursoNoEncontradoException("Disco", request.getIdDisco()))
            : null;

        String descripcion = disco != null
            ? null
            : (request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        if (disco == null && (descripcion == null || descripcion.isBlank())) {
            throw new NegocioException("Seleccioná un disco o ingresá una descripción para la pre-venta");
        }

        String codigo = disco != null ? disco.getCodigoInterno() : request.getCodigoDisco();
        codigo = codigo != null && !codigo.trim().isBlank() ? codigo.trim().replaceAll("\\s+", " ") : null;
        PreVenta preVenta = PreVenta.builder()
            .cliente(cliente)
            .disco(disco)
            .fecha(request.getFecha() != null ? request.getFecha() : LocalDate.now())
            .cantidad(request.getCantidad())
            .precio(request.getPrecio())
            .estado("PENDIENTE")
            .codigoDisco(codigo)
            .codigoDiscoNormalizado(PreVentaCodeMatcher.normalize(codigo))
            .notas(request.getNotas())
            .artistaSnap(disco != null ? disco.getArtista() : null)
            .albumSnap(disco != null ? disco.getAlbum() : null)
            .descripcionSnap(descripcion)
            .build();
        return toDto(repository.save(preVenta));
    }

    public VentaResponseDTO actualizarPago(Long id, PreVentaPagoUpdateRequestDTO request) {
        PreVenta preVenta = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pre-venta", id));
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
        PreVenta preVenta = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pre-venta", id));
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

    private Venta validarPago(PreVenta preVenta, Long id) {
        if (!"PAGADA".equals(preVenta.getEstado()) || preVenta.getVentaPago() == null) {
            throw new ConflictoNegocioException("La pre-venta no tiene un cobro pagado asociado");
        }
        Venta venta = preVenta.getVentaPago();
        if (!"PRE_VENTA".equals(venta.getOrigen())
                || !Objects.equals(id, venta.getIdPreVentaOrigen())) {
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
            throw new ConflictoNegocioException("El cobro de la pre-venta tiene movimientos relacionados no soportados");
        }
        // PagoDeuda has no direct sale FK; any such row belongs to the
        // rejected Deuda relation above.
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

    private VentaResponseDTO mapearVenta(Venta venta) {
        return VentaResponseDTO.builder()
            .idVenta(venta.getIdVenta())
            .idCliente(venta.getCliente().getIdCliente())
            .fechaVenta(venta.getFechaVenta())
            .medioPago(venta.getMedioPago() != null ? venta.getMedioPago().name() : null)
            .numeroRecibo(venta.getNumeroRecibo())
            .montoPagado(venta.getMontoPagado())
            .montoDeuda(venta.getMontoDeuda())
            .estadoPago(venta.getEstadoPago() != null ? venta.getEstadoPago().name() : null)
            .totalFinal(venta.getTotalFinal())
            .tipoMovimiento("PRE_VENTA")
            .descripcionMovimiento("Cobro de pre-venta")
            .montoMovimiento(venta.getMontoPagado())
            .origen(venta.getOrigen())
            .idPreVentaOrigen(venta.getIdPreVentaOrigen())
            .observaciones(venta.getObservaciones())
            .build();
    }

    public PreVentaResponseDTO marcarPagada(Long id) {
        PreVenta preVenta = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pre-venta", id));
        if ("PAGADA".equals(preVenta.getEstado()) || preVenta.getVentaPago() != null) {
            throw new NegocioException("La pre-venta ya fue marcada como pagada");
        }
        if (preVenta.getPrecio() == null || preVenta.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("La pre-venta no tiene un importe válido");
        }
        int cantidad = preVenta.getCantidad() != null ? preVenta.getCantidad() : 1;
        if (cantidad <= 0) throw new NegocioException("La cantidad debe ser positiva");

        LocalDateTime fechaPago = LocalDateTime.now();
        String clienteNombre = (preVenta.getCliente().getNombre() + " "
            + (preVenta.getCliente().getApellido() != null ? preVenta.getCliente().getApellido() : "")).trim();
        Venta venta = Venta.builder()
            .cliente(preVenta.getCliente()).fechaVenta(fechaPago).canalVenta(CanalVenta.LOCAL)
            .total(preVenta.getPrecio()).precioVenta(preVenta.getPrecio()).totalFinal(preVenta.getPrecio())
            .costoDisco(costoHistorico(preVenta.getDisco(), cantidad))
            .subtotal(preVenta.getPrecio()).descuentoPorcentaje(BigDecimal.ZERO)
            .costoEnvio(BigDecimal.ZERO).otrosCostos(BigDecimal.ZERO).montoImpuesto(BigDecimal.ZERO)
            .gananciaEstimada(null).tipoEntrega(TipoEntrega.RETIRO)
            .estado(EstadoVenta.COMPLETADA).observaciones("Cobro de pre-venta #" + id)
            .numeroFactura("PV-" + id).clienteNombreSnapshot(clienteNombre)
            .medioPago(MedioPago.OTRO).montoPagado(preVenta.getPrecio()).montoDeuda(BigDecimal.ZERO)
            .estadoPago(EstadoPago.PAGADO).origen("PRE_VENTA").idPreVentaOrigen(id).build();
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
            .descripcionSnap(preVenta.getDescripcionSnap()).codigoSnap(preVenta.getCodigoDisco()).build();
        venta.getDetalles().add(detalleVentaRepository.save(detalle));

        preVenta.setEstado("PAGADA");
        preVenta.setFechaPago(fechaPago);
        preVenta.setVentaPago(venta);
        return toDto(repository.save(preVenta));
    }

    private BigDecimal costoHistorico(Disco disco, int cantidad) {
        if (disco == null) {
            return null;
        }
        AcquisitionCostResolution acquisition = profitCalculationService.acquisitionCostForDisco(disco);
        return acquisition.isComplete()
            ? acquisition.unitCostUyu().multiply(BigDecimal.valueOf(cantidad)) : null;
    }

    public void eliminar(Long id) {
        PreVenta preVenta = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pre-venta", id));
        if ("PAGADA".equals(preVenta.getEstado()) || preVenta.getVentaPago() != null) {
            throw new NegocioException("No se puede eliminar una pre-venta pagada");
        }
        repository.delete(preVenta);
    }

    private PreVentaResponseDTO toDto(PreVenta preVenta) {
        Cliente cliente = preVenta.getCliente();
        Disco disco = preVenta.getDisco();
        return PreVentaResponseDTO.builder()
            .idPreVenta(preVenta.getIdPreVenta())
            .idCliente(cliente.getIdCliente())
            .clienteNombre((cliente.getNombre() + " " + (cliente.getApellido() != null ? cliente.getApellido() : "")).trim())
            .idDisco(disco != null ? disco.getIdDisco() : null)
            .artista(disco != null ? disco.getArtista() : preVenta.getArtistaSnap())
            .album(disco != null ? disco.getAlbum() : preVenta.getAlbumSnap())
            .descripcion(preVenta.getDescripcionSnap())
            .codigoDisco(preVenta.getCodigoDisco())
            .idVentaPago(preVenta.getVentaPago() != null ? preVenta.getVentaPago().getIdVenta() : null)
            .fechaPago(preVenta.getFechaPago())
            .cantidad(preVenta.getCantidad())
            .precio(preVenta.getPrecio())
            .fecha(preVenta.getFecha())
            .estado(preVenta.getEstado())
            .notas(preVenta.getNotas())
            .build();
    }
}
