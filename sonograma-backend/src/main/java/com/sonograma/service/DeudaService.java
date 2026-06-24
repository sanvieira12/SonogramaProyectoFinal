package com.sonograma.service;

import com.sonograma.dto.DeudaRequestDTO;
import com.sonograma.dto.DeudaResponseDTO;
import com.sonograma.dto.PagoDeudaDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
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
    public List<DeudaResponseDTO> obtenerPendientes(String q) {
        List<Deuda> deudas = (q != null && !q.isBlank())
                ? deudaRepository.buscarPendientes(q)
                : deudaRepository.findByEstadoPagoNot(EstadoPago.PAGADO);
        return deudas.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeudaResponseDTO> obtenerPorCliente(Long idCliente) {
        return deudaRepository.findByClienteIdClienteOrderByFechaCreacionDesc(idCliente)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeudaResponseDTO obtenerPorId(Long idDeuda) {
        return deudaRepository.findById(idDeuda)
                .map(this::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen() {
        List<Deuda> pendientes = deudaRepository.findByEstadoPagoNot(EstadoPago.PAGADO);
        BigDecimal totalPendiente = pendientes.stream()
                .map(Deuda::getMontoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantDeudores = pendientes.stream()
                .map(this::deudorKey)
                .distinct().count();
        BigDecimal mayorDeuda = pendientes.stream()
                .map(Deuda::getMontoPendiente)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return Map.of(
                "totalPendiente", totalPendiente,
                "cantDeudores", cantDeudores,
                "mayorDeuda", mayorDeuda,
                "cantDeudas", pendientes.size()
        );
    }

    public DeudaResponseDTO crear(DeudaRequestDTO request) {
        Deuda deuda = new Deuda();
        aplicarRequest(deuda, request, true);
        return toDTO(deudaRepository.save(deuda));
    }

    public DeudaResponseDTO actualizar(Long idDeuda, DeudaRequestDTO request) {
        Deuda deuda = deudaRepository.findById(idDeuda)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));
        aplicarRequest(deuda, request, false);
        return toDTO(deudaRepository.save(deuda));
    }

    public DeudaResponseDTO registrarPago(Long idDeuda, BigDecimal monto, String notas) {
        Deuda deuda = deudaRepository.findById(idDeuda)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deuda", idDeuda));

        if (deuda.getEstadoPago() == EstadoPago.PAGADO) {
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
                .build();
        pagoDeudaRepository.save(pago);

        BigDecimal nuevoPagado = deuda.getMontoPagado().add(monto);
        BigDecimal nuevoPendiente = deuda.getMontoTotal().subtract(nuevoPagado);
        if (nuevoPendiente.compareTo(BigDecimal.ZERO) < 0) nuevoPendiente = BigDecimal.ZERO;

        deuda.setMontoPagado(nuevoPagado);
        deuda.setMontoPendiente(nuevoPendiente);
        deuda.setFechaUltimoPago(LocalDate.now());
        deuda.setUpdatedAt(LocalDateTime.now());
        deuda.setEstadoPago(nuevoPendiente.compareTo(BigDecimal.ZERO) == 0 ? EstadoPago.PAGADO : EstadoPago.PARCIAL);
        if (notas != null && !notas.isBlank()) deuda.setNotas(notas);

        // Sync back to Venta
        if (deuda.getVenta() != null) {
            deuda.getVenta().setMontoPagado(nuevoPagado);
            deuda.getVenta().setMontoDeuda(nuevoPendiente);
            deuda.getVenta().setEstadoPago(deuda.getEstadoPago());
        }

        return toDTO(deudaRepository.save(deuda));
    }

    private void aplicarRequest(Deuda deuda, DeudaRequestDTO request, boolean creando) {
        if (request.getIdCliente() != null) {
            Cliente cliente = clienteRepository.findById(request.getIdCliente())
                    .filter(Cliente::getActivo)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.getIdCliente()));
            deuda.setCliente(cliente);
        } else if (creando || request.getNombreDeudorManual() != null) {
            deuda.setCliente(null);
        }

        if (request.getNombreDeudorManual() != null) deuda.setNombreDeudorManual(textoNulo(request.getNombreDeudorManual()));
        if (request.getMailManual() != null) deuda.setMailManual(textoNulo(request.getMailManual()));
        if (request.getInstagramManual() != null) deuda.setInstagramManual(textoNulo(request.getInstagramManual()));
        if (request.getCiManual() != null) deuda.setCiManual(textoNulo(request.getCiManual()));
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
        List<PagoDeudaDTO> pagos = pagoDeudaRepository
                .findByDeudaIdDeudaOrderByFechaPagoDescCreatedAtDesc(d.getIdDeuda())
                .stream()
                .map(this::toPagoDTO)
                .toList();
        return DeudaResponseDTO.builder()
                .idDeuda(d.getIdDeuda())
                .idVenta(d.getVenta() != null ? d.getVenta().getIdVenta() : null)
                .numeroFactura(!isBlank(d.getNumeroFactura()) ? d.getNumeroFactura() : (d.getVenta() != null ? d.getVenta().getNumeroFactura() : null))
                .idCliente(d.getCliente() != null ? d.getCliente().getIdCliente() : null)
                .nombreCliente(nombreDeudor(d))
                .nombreDeudorManual(d.getNombreDeudorManual())
                .mailManual(d.getMailManual())
                .instagramManual(d.getInstagramManual())
                .ciManual(d.getCiManual())
                .descripcion(d.getDescripcion())
                .montoTotal(d.getMontoTotal())
                .montoPagado(d.getMontoPagado())
                .montoPendiente(d.getMontoPendiente())
                .fechaVenta(d.getFechaVenta())
                .fechaDeuda(d.getFechaDeuda())
                .fechaUltimoPago(d.getFechaUltimoPago())
                .fechaCreacion(d.getFechaCreacion())
                .updatedAt(d.getUpdatedAt())
                .estadoPago(d.getEstadoPago().name())
                .notas(d.getNotas())
                .pagos(pagos)
                .build();
    }

    private PagoDeudaDTO toPagoDTO(PagoDeuda pago) {
        return PagoDeudaDTO.builder()
                .idPagoDeuda(pago.getIdPagoDeuda())
                .monto(pago.getMonto())
                .fechaPago(pago.getFechaPago())
                .notas(pago.getNotas())
                .createdAt(pago.getCreatedAt())
                .build();
    }

    private String nombreDeudor(Deuda d) {
        if (d.getCliente() == null) return d.getNombreDeudorManual();
        return (d.getCliente().getNombre()
                + (d.getCliente().getApellido() != null ? " " + d.getCliente().getApellido() : "")).trim();
    }

    private String deudorKey(Deuda d) {
        if (d.getCliente() != null) return "cliente:" + d.getCliente().getIdCliente();
        return "manual:" + Objects.toString(d.getNombreDeudorManual(), d.getIdDeuda().toString()).toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String textoNulo(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
