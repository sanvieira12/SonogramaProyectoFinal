package com.sonograma.service;

import com.sonograma.dto.DeudaRequestDTO;
import com.sonograma.dto.DeudaConsolidadaResponseDTO;
import com.sonograma.dto.DeudaResponseDTO;
import com.sonograma.dto.DetalleVentaResponseDTO;
import com.sonograma.dto.PagoDeudaDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.EstadoPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeudaService {

    private final DeudaRepository deudaRepository;
    private final ClienteRepository clienteRepository;
    private final PagoDeudaRepository pagoDeudaRepository;

    @Transactional(readOnly = true)
    public List<DeudaConsolidadaResponseDTO> obtenerPendientes(String q) {
        List<Deuda> candidates = (q != null && !q.isBlank())
                ? deudaRepository.buscarPendientes(q)
                : deudaRepository.findAllByActivaTrueOrderByFechaDeudaDescFechaCreacionDesc();

        // A search may match just one movement. Expand matching customer IDs
        // before grouping so opening a result still shows the complete history.
        List<Deuda> deudas = new ArrayList<>(candidates);
        if (q != null && !q.isBlank()) {
            List<Long> idClientes = candidates.stream()
                    .map(Deuda::getCliente)
                    .filter(Objects::nonNull)
                    .map(Cliente::getIdCliente)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!idClientes.isEmpty()) {
                deudaRepository.findAllByActivaTrueAndClienteIdClienteInOrderByFechaDeudaDescFechaCreacionDesc(idClientes)
                        .forEach(d -> { if (deudas.stream().noneMatch(existing -> Objects.equals(existing.getIdDeuda(), d.getIdDeuda()))) deudas.add(d); });
            }
        }

        return consolidar(deudas);
    }

    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerPorCliente(Long idCliente) {
        return deudaRepository.findByClienteIdClienteAndActivaTrueOrderByFechaCreacionDesc(idCliente)
                .stream().filter(this::esMovimientoVigente).map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeudaResponseDTO obtenerPorId(Long idDeuda) {
        return deudaRepository.findById(idDeuda)
                .filter(d -> Boolean.TRUE.equals(d.getActiva()))
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen() {
        List<DeudaResponseDTO> movimientos = deudaRepository.findAllByActivaTrueOrderByFechaDeudaDescFechaCreacionDesc()
                .stream()
                .filter(this::esMovimientoVigente)
                .map(this::toDTO)
                .filter(d -> d.getMontoPendiente().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        List<DeudaConsolidadaResponseDTO> consolidadas = consolidarDTOs(movimientos);
        BigDecimal totalPendiente = consolidadas.stream()
                .map(DeudaConsolidadaResponseDTO::getMontoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantDeudores = consolidadas.size();
        BigDecimal mayorDeuda = consolidadas.stream()
                .map(DeudaConsolidadaResponseDTO::getMontoPendiente)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return Map.of(
                "totalPendiente", totalPendiente,
                "cantDeudores", cantDeudores,
                "mayorDeuda", mayorDeuda,
                "cantDeudas", movimientos.size()
        );
    }

    public DeudaResponseDTO crear(DeudaRequestDTO request) {
        Deuda deuda = new Deuda();
        aplicarRequest(deuda, request, true);
        deuda.setMontoPagadoInicial(Objects.requireNonNullElse(deuda.getMontoPagado(), BigDecimal.ZERO));
        Deuda saved = deudaRepository.save(deuda);
        return toDTO(saved);
    }

    public DeudaResponseDTO actualizar(Long idDeuda, DeudaRequestDTO request) {
        Deuda deuda = deudaRepository.findById(idDeuda)
                .filter(d -> Boolean.TRUE.equals(d.getActiva()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));
        aplicarRequest(deuda, request, false);
        deuda.setMontoPagadoInicial(inicialDesdeMontoActual(deuda));
        recalcularEstado(deuda);
        return toDTO(deudaRepository.save(deuda));
    }

    /**
     * Central debt writer used by every sale path. A debt row is a movement,
     * not a customer container; the customer is consolidated when read.
     */
    public void sincronizarVenta(Venta venta, Cliente cliente, BigDecimal total, BigDecimal montoPagado,
                                 BigDecimal montoDeuda, EstadoPago estadoPago, LocalDateTime fechaVenta) {
        Cliente clienteBloqueado = clienteRepository.findByIdForUpdate(cliente.getIdCliente())
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", cliente.getIdCliente()));
        Deuda deuda = deudaRepository.findByVentaIdVenta(venta.getIdVenta()).orElse(null);
        if (deuda == null && montoDeuda.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (deuda == null) {
            deuda = new Deuda();
            deuda.setVenta(venta);
            deuda.setFechaCreacion(LocalDateTime.now());
            deuda.setMontoPagadoInicial(Objects.requireNonNullElse(montoPagado, BigDecimal.ZERO));
        } else {
            // Set the new sale total before clamping the recovered initial payment.
            deuda.setMontoTotal(total);
            deuda.setMontoPagadoInicial(inicialDesdeMontoActual(deuda, montoPagado));
        }
        deuda.setCliente(clienteBloqueado);
        deuda.setMontoTotal(total);
        deuda.setFechaVenta(fechaVenta.toLocalDate());
        deuda.setFechaDeuda(fechaVenta.toLocalDate());
        deuda.setActiva(true);
        deuda.setUpdatedAt(LocalDateTime.now());
        recalcularEstado(deuda);
        deudaRepository.save(deuda);
    }

    public void eliminar(Long idDeuda) {
        Deuda deuda = deudaRepository.findById(idDeuda)
                .filter(d -> Boolean.TRUE.equals(d.getActiva()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));
        deuda.setActiva(false);
        deuda.setUpdatedAt(LocalDateTime.now());
        deudaRepository.save(deuda);
    }

    public DeudaResponseDTO registrarPago(Long idDeuda, BigDecimal monto, String notas) {
        return registrarPago(idDeuda, monto, notas, null, null);
    }

    public DeudaResponseDTO registrarPago(Long idDeuda, BigDecimal monto, String notas,
                                          String numeroRecibo, String idempotencyKey) {
        Deuda deuda = deudaRepository.findByIdForUpdate(idDeuda)
                .filter(d -> Boolean.TRUE.equals(d.getActiva()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));

        String normalizedIdempotencyKey = textoNulo(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            PagoDeuda pagoExistente = pagoDeudaRepository
                    .findByDeudaIdDeudaAndIdempotencyKey(idDeuda, normalizedIdempotencyKey)
                    .orElse(null);
            if (pagoExistente != null) {
                return toDTO(deuda);
            }
        }

        recalcularEstado(deuda);
        if (deuda.getMontoPendiente().compareTo(BigDecimal.ZERO) == 0) {
            throw new NegocioException("La deuda ya está pagada");
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El monto del pago debe ser positivo");
        }
        if (monto.compareTo(deuda.getMontoPendiente()) > 0) {
            throw new NegocioException("El monto excede la deuda pendiente");
        }

        PagoDeuda pago = PagoDeuda.builder()
                .deuda(deuda)
                .monto(monto)
                .fechaPago(LocalDate.now())
                .notas(textoNulo(notas))
                .numeroRecibo(textoNulo(numeroRecibo))
                .idempotencyKey(normalizedIdempotencyKey)
                .build();
        pagoDeudaRepository.save(pago);

        deuda.setFechaUltimoPago(LocalDate.now());
        deuda.setUpdatedAt(LocalDateTime.now());
        if (notas != null && !notas.isBlank()) deuda.setNotas(notas);
        recalcularEstado(deuda);
        return toDTO(deudaRepository.save(deuda));
    }

    public void eliminarPago(Long idPagoDeuda) {
        PagoDeuda pago = pagoDeudaRepository.findByIdPagoDeudaForUpdate(idPagoDeuda)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago de deuda", idPagoDeuda));

        if (Boolean.TRUE.equals(pago.getAnulado())) {
            throw new NegocioException("El pago de deuda ya fue anulado");
        }

        Deuda deudaRelacionada = pago.getDeuda();
        if (deudaRelacionada == null || deudaRelacionada.getIdDeuda() == null) {
            throw new NegocioException("El pago de deuda no tiene una deuda asociada");
        }
        Deuda deuda = deudaRepository.findByIdForUpdate(deudaRelacionada.getIdDeuda())
                .filter(d -> Boolean.TRUE.equals(d.getActiva()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", deudaRelacionada.getIdDeuda()));

        pagoDeudaRepository.delete(pago);
        pagoDeudaRepository.flush();

        LocalDate fechaUltimoPago = pagoDeudaRepository
                .findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(deuda.getIdDeuda()).stream()
                .findFirst().map(PagoDeuda::getFechaPago).orElse(null);

        deuda.setFechaUltimoPago(fechaUltimoPago);
        deuda.setUpdatedAt(LocalDateTime.now());
        if (deuda.getMontoPagadoInicial() == null) deuda.setMontoPagadoInicial(BigDecimal.ZERO);
        recalcularEstado(deuda);
        deudaRepository.save(deuda);
    }

    private void aplicarRequest(Deuda deuda, DeudaRequestDTO request, boolean creando) {
        Cliente cliente = resolveCliente(request);
        if (cliente != null) {
            deuda.setCliente(cliente);
            deuda.setNombreDeudorManual(null);
            deuda.setMailManual(textoNulo(request.getMailManual()) != null ? textoNulo(request.getMailManual()) : textoNulo(cliente.getEmail()));
            deuda.setInstagramManual(textoNulo(request.getInstagramManual()) != null ? textoNulo(request.getInstagramManual()) : textoNulo(cliente.getInstagramUsuario()));
            deuda.setCiManual(textoNulo(request.getCiManual()) != null ? textoNulo(request.getCiManual()) : textoNulo(cliente.getCedula()));
        } else if (creando || request.getNombreDeudorManual() != null) {
            deuda.setCliente(null);
            if (request.getNombreDeudorManual() != null) deuda.setNombreDeudorManual(textoNulo(request.getNombreDeudorManual()));
            if (request.getMailManual() != null) deuda.setMailManual(textoNulo(request.getMailManual()));
            if (request.getInstagramManual() != null) deuda.setInstagramManual(textoNulo(request.getInstagramManual()));
            if (request.getCiManual() != null) deuda.setCiManual(textoNulo(request.getCiManual()));
        }

        if (request.getDescripcion() != null) deuda.setDescripcion(textoNulo(request.getDescripcion()));
        if (request.getNumeroFactura() != null) deuda.setNumeroFactura(textoNulo(request.getNumeroFactura()));
        if (request.getNotas() != null) deuda.setNotas(textoNulo(request.getNotas()));
        if (request.getFechaDeuda() != null) deuda.setFechaDeuda(request.getFechaDeuda());
        if (request.getFechaVenta() != null) deuda.setFechaVenta(request.getFechaVenta());

        if (deuda.getCliente() == null && isBlank(deuda.getNombreDeudorManual())) {
            throw new NegocioException("Seleccioná un cliente o ingresá un nombre de deudor");
        }

        if (request.getMontoTotal() != null) {
            if (request.getMontoTotal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new NegocioException("El total de la deuda debe ser positivo");
            }
            deuda.setMontoTotal(request.getMontoTotal());
        } else if (creando) {
            throw new NegocioException("El total de la deuda es obligatorio");
        }

        if (request.getMontoPagado() != null) {
            if (request.getMontoPagado().compareTo(BigDecimal.ZERO) < 0) {
                throw new NegocioException("El monto pagado no puede ser negativo");
            }
            deuda.setMontoPagado(request.getMontoPagado());
        } else if (creando && deuda.getMontoPagado() == null) {
            deuda.setMontoPagado(BigDecimal.ZERO);
        }

        BigDecimal total = Objects.requireNonNullElse(deuda.getMontoTotal(), BigDecimal.ZERO);
        BigDecimal pagado = Objects.requireNonNullElse(deuda.getMontoPagado(), BigDecimal.ZERO);
        if (pagado.compareTo(total) > 0) {
            throw new NegocioException("El monto pagado no puede superar el total");
        }
        deuda.setMontoPendiente(total.subtract(pagado));

        EstadoPago estado = request.getEstadoPago() != null && !request.getEstadoPago().isBlank()
                ? EstadoPago.valueOf(request.getEstadoPago())
                : estadoDesdeMontos(total, pagado);
        deuda.setEstadoPago(estado);

        if (deuda.getFechaDeuda() == null) deuda.setFechaDeuda(LocalDate.now());
        if (deuda.getFechaVenta() == null) deuda.setFechaVenta(deuda.getFechaDeuda());
        if (deuda.getFechaCreacion() == null) deuda.setFechaCreacion(LocalDateTime.now());
        deuda.setUpdatedAt(LocalDateTime.now());
    }

    private EstadoPago estadoDesdeMontos(BigDecimal total, BigDecimal pagado) {
        if (pagado.compareTo(total) >= 0) return EstadoPago.PAGADO;
        if (pagado.compareTo(BigDecimal.ZERO) > 0) return EstadoPago.PARCIAL;
        return EstadoPago.PENDIENTE;
    }

    private DeudaResponseDTO toDTO(Deuda d) {
        List<PagoDeuda> pagosOrigen = pagosDeDeuda(d);
        Balance balance = calcularBalance(d, pagosOrigen);
        List<PagoDeudaDTO> pagos = pagosOrigen.stream()
                .filter(p -> p.getMonto() != null && p.getMonto().compareTo(BigDecimal.ZERO) > 0)
                .map(this::toPagoDTO)
                .toList();
        return DeudaResponseDTO.builder()
                .idDeuda(d.getIdDeuda())
                .idVenta(d.getVenta() != null ? d.getVenta().getIdVenta() : null)
                .numeroFactura(!isBlank(d.getNumeroFactura()) ? d.getNumeroFactura() : (d.getVenta() != null ? d.getVenta().getNumeroFactura() : null))
                .numeroRecibo(d.getVenta() != null ? d.getVenta().getNumeroRecibo() : null)
                .idCliente(d.getCliente() != null ? d.getCliente().getIdCliente() : null)
                .nombreCliente(nombreDeudor(d))
                .nombreDeudorManual(d.getNombreDeudorManual())
                .mailManual(!isBlank(d.getMailManual()) ? d.getMailManual() : (d.getCliente() != null ? d.getCliente().getEmail() : null))
                .instagramManual(!isBlank(d.getInstagramManual()) ? d.getInstagramManual() : (d.getCliente() != null ? d.getCliente().getInstagramUsuario() : null))
                .ciManual(!isBlank(d.getCiManual()) ? d.getCiManual() : (d.getCliente() != null ? d.getCliente().getCedula() : null))
                .descripcion(d.getDescripcion())
                .montoTotal(balance.total())
                .montoPagado(balance.pagado())
                .montoPendiente(balance.pendiente())
                .fechaVenta(d.getFechaVenta())
                .fechaDeuda(d.getFechaDeuda())
                .fechaUltimoPago(d.getFechaUltimoPago())
                .fechaCreacion(d.getFechaCreacion())
                .updatedAt(d.getUpdatedAt())
                .estadoPago(balance.estado().name())
                .notas(d.getNotas())
                .pagos(pagos)
                .detalles(d.getVenta() != null && d.getVenta().getDetalles() != null
                        ? d.getVenta().getDetalles().stream().map(detalle -> DetalleVentaResponseDTO.builder()
                                .idDetalle(detalle.getIdDetalle())
                                .idDisco(detalle.getDisco() != null ? detalle.getDisco().getIdDisco() : null)
                                .artista(detalle.getArtistaSnap() != null ? detalle.getArtistaSnap() : (detalle.getDisco() != null ? detalle.getDisco().getArtista() : null))
                                .album(detalle.getAlbumSnap() != null ? detalle.getAlbumSnap() : (detalle.getDisco() != null ? detalle.getDisco().getAlbum() : null))
                                .descripcion(detalle.getDescripcionSnap())
                                .codigoInterno(detalle.getCodigoSnap() != null ? detalle.getCodigoSnap() : (detalle.getDisco() != null ? detalle.getDisco().getCodigoInterno() : null))
                                .imagenUrl(detalle.getDisco() != null ? detalle.getDisco().getImagenUrl() : null)
                                .cantidad(detalle.getCantidad())
                                .precioUnitario(detalle.getPrecioUnitario())
                                .manualItem(Boolean.TRUE.equals(detalle.getManualItem()) || detalle.getDisco() == null)
                                .build()).toList()
                        : List.of())
                .movimientos(null)
                .build();
    }

    private List<DeudaConsolidadaResponseDTO> consolidar(List<Deuda> deudas) {
        List<DeudaResponseDTO> movimientos = deudas.stream()
                .filter(this::esMovimientoVigente)
                .map(this::toDTO)
                .toList();
        return consolidarDTOs(movimientos);
    }

    private List<DeudaConsolidadaResponseDTO> consolidarDTOs(List<DeudaResponseDTO> movimientos) {
        Map<String, List<DeudaResponseDTO>> porGrupo = movimientos.stream()
                .collect(Collectors.groupingBy(this::grupoKey, LinkedHashMap::new, Collectors.toList()));
        return porGrupo.values().stream()
                .map(this::consolidarGrupo)
                .filter(d -> d.getMontoPendiente().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(DeudaConsolidadaResponseDTO::getFechaDeuda,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private DeudaConsolidadaResponseDTO consolidarGrupo(List<DeudaResponseDTO> movimientos) {
        DeudaResponseDTO primero = movimientos.get(0);
        BigDecimal total = movimientos.stream().map(DeudaResponseDTO::getMontoTotal)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = movimientos.stream().map(DeudaResponseDTO::getMontoPagado)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendiente = movimientos.stream().map(DeudaResponseDTO::getMontoPendiente)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        String estado = estadoDesdeMontos(total, pagado).name();
        return DeudaConsolidadaResponseDTO.builder()
                .idDeuda(primero.getIdDeuda())
                .grupoKey(grupoKey(primero))
                .idCliente(primero.getIdCliente())
                .nombreCliente(primero.getNombreCliente())
                .nombreDeudorManual(primero.getNombreDeudorManual())
                .mailManual(primero.getMailManual())
                .instagramManual(primero.getInstagramManual())
                .ciManual(primero.getCiManual())
                .montoTotal(total)
                .montoPagado(pagado)
                .montoPendiente(pendiente)
                .fechaVenta(movimientos.stream().map(DeudaResponseDTO::getFechaVenta).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null))
                .fechaDeuda(movimientos.stream().map(DeudaResponseDTO::getFechaDeuda).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null))
                .fechaUltimoPago(movimientos.stream().map(DeudaResponseDTO::getFechaUltimoPago).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null))
                .fechaCreacion(movimientos.stream().map(DeudaResponseDTO::getFechaCreacion).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null))
                .updatedAt(movimientos.stream().map(DeudaResponseDTO::getUpdatedAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null))
                .estadoPago(estado)
                .cantidadMovimientos(movimientos.size())
                .movimientos(movimientos)
                .build();
    }

    private String grupoKey(DeudaResponseDTO deuda) {
        return deuda.getIdCliente() != null ? "cliente:" + deuda.getIdCliente() : "manual:" + deuda.getIdDeuda();
    }

    private boolean esMovimientoVigente(Deuda deuda) {
        return Boolean.TRUE.equals(deuda.getActiva())
                && (deuda.getVenta() == null || deuda.getVenta().getEstado() != EstadoVenta.CANCELADA);
    }

    private List<PagoDeuda> pagosDeDeuda(Deuda deuda) {
        if (deuda.getIdDeuda() == null) return List.of();
        List<PagoDeuda> pagos = pagoDeudaRepository.findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(deuda.getIdDeuda());
        return pagos == null ? List.of() : pagos.stream()
                .filter(p -> p != null && !Boolean.TRUE.equals(p.getAnulado()))
                .toList();
    }

    private Balance calcularBalance(Deuda deuda, List<PagoDeuda> pagos) {
        BigDecimal total = Objects.requireNonNullElse(deuda.getMontoTotal(), BigDecimal.ZERO).max(BigDecimal.ZERO);
        BigDecimal inicial = Objects.requireNonNullElse(deuda.getMontoPagadoInicial(), BigDecimal.ZERO).max(BigDecimal.ZERO);
        BigDecimal pagosValidos = pagos.stream()
                .filter(p -> p != null && p.getMonto() != null && p.getMonto().compareTo(BigDecimal.ZERO) > 0)
                .map(PagoDeuda::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pagado = inicial.add(pagosValidos).min(total);
        BigDecimal pendiente = total.subtract(pagado).max(BigDecimal.ZERO);
        return new Balance(total, pagado, pendiente, estadoDesdeMontos(total, pagado));
    }

    private void recalcularEstado(Deuda deuda) {
        Balance balance = calcularBalance(deuda, pagosDeDeuda(deuda));
        deuda.setMontoTotal(balance.total());
        deuda.setMontoPagado(balance.pagado());
        deuda.setMontoPendiente(balance.pendiente());
        deuda.setEstadoPago(balance.estado());
        if (deuda.getVenta() != null) {
            deuda.getVenta().setMontoPagado(balance.pagado());
            deuda.getVenta().setMontoDeuda(balance.pendiente());
            deuda.getVenta().setEstadoPago(balance.estado());
        }
    }

    private BigDecimal inicialDesdeMontoActual(Deuda deuda) {
        return inicialDesdeMontoActual(deuda, Objects.requireNonNullElse(deuda.getMontoPagado(), BigDecimal.ZERO));
    }

    private BigDecimal inicialDesdeMontoActual(Deuda deuda, BigDecimal montoActual) {
        BigDecimal pagos = pagosDeDeuda(deuda).stream()
                .filter(p -> p != null && p.getMonto() != null && p.getMonto().compareTo(BigDecimal.ZERO) > 0)
                .map(PagoDeuda::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = Objects.requireNonNullElse(deuda.getMontoTotal(), BigDecimal.ZERO);
        return montoActual.subtract(pagos).max(BigDecimal.ZERO).min(total);
    }

    private record Balance(BigDecimal total, BigDecimal pagado, BigDecimal pendiente, EstadoPago estado) {}

    private PagoDeudaDTO toPagoDTO(PagoDeuda pago) {
        return PagoDeudaDTO.builder()
                .idPagoDeuda(pago.getIdPagoDeuda())
                .monto(pago.getMonto())
                .fechaPago(pago.getFechaPago())
                .notas(pago.getNotas())
                .numeroRecibo(pago.getNumeroRecibo())
                .createdAt(pago.getCreatedAt())
                .build();
    }

    private String nombreDeudor(Deuda d) {
        if (d.getCliente() == null) return d.getNombreDeudorManual();
        return (d.getCliente().getNombre()
                + (d.getCliente().getApellido() != null ? " " + d.getCliente().getApellido() : "")).trim();
    }

    private Cliente resolveCliente(DeudaRequestDTO request) {
        if (request.getIdCliente() != null) {
            return clienteRepository.findById(request.getIdCliente())
                    .filter(Cliente::getActivo)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.getIdCliente()));
        }

        String nombre = textoNulo(request.getNombreDeudorManual());
        String email = textoNulo(request.getMailManual());
        String cedula = textoNulo(request.getCiManual());
        String instagram = textoNulo(request.getInstagramManual());
        if (isBlank(nombre) && isBlank(email) && isBlank(cedula) && isBlank(instagram)) {
            return null;
        }

        if (isBlank(nombre)) {
            throw new NegocioException("Ingresá el nombre del cliente para registrar la deuda");
        }

        String[] parts = splitNombre(nombre);
        Cliente nuevo = new Cliente();
        nuevo.setNombre(parts[0]);
        nuevo.setApellido(parts[1]);
        nuevo.setCedula(cedula);
        nuevo.setEmail(email);
        nuevo.setInstagramUsuario(instagram);
        nuevo.setActivo(true);
        return clienteRepository.save(nuevo);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String textoNulo(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String[] splitNombre(String nombreCompleto) {
        String[] parts = Arrays.stream(nombreCompleto.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .toArray(String[]::new);
        String nombre = parts.length == 0 ? null : parts[0];
        String apellido = parts.length <= 1 ? null : String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        return new String[] { nombre, apellido };
    }
}
