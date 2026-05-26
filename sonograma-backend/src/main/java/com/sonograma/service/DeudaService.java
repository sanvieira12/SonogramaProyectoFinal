package com.sonograma.service;

import com.sonograma.dto.DeudaResponseDTO;
import com.sonograma.entity.Deuda;
import com.sonograma.enums.EstadoPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DeudaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeudaService {

    private final DeudaRepository deudaRepository;

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
    public Map<String, Object> obtenerResumen() {
        List<Deuda> pendientes = deudaRepository.findByEstadoPagoNot(EstadoPago.PAGADO);
        BigDecimal totalPendiente = pendientes.stream()
                .map(Deuda::getMontoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantDeudores = pendientes.stream()
                .map(d -> d.getCliente().getIdCliente())
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

        BigDecimal nuevoPagado = deuda.getMontoPagado().add(monto);
        BigDecimal nuevoPendiente = deuda.getMontoTotal().subtract(nuevoPagado);
        if (nuevoPendiente.compareTo(BigDecimal.ZERO) < 0) nuevoPendiente = BigDecimal.ZERO;

        deuda.setMontoPagado(nuevoPagado);
        deuda.setMontoPendiente(nuevoPendiente);
        deuda.setFechaUltimoPago(LocalDate.now());
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

    private DeudaResponseDTO toDTO(Deuda d) {
        return DeudaResponseDTO.builder()
                .idDeuda(d.getIdDeuda())
                .idVenta(d.getVenta() != null ? d.getVenta().getIdVenta() : null)
                .numeroFactura(d.getVenta() != null ? d.getVenta().getNumeroFactura() : null)
                .idCliente(d.getCliente().getIdCliente())
                .nombreCliente(d.getCliente().getNombre() + (d.getCliente().getApellido() != null ? " " + d.getCliente().getApellido() : ""))
                .montoTotal(d.getMontoTotal())
                .montoPagado(d.getMontoPagado())
                .montoPendiente(d.getMontoPendiente())
                .fechaVenta(d.getFechaVenta())
                .fechaUltimoPago(d.getFechaUltimoPago())
                .fechaCreacion(d.getFechaCreacion())
                .estadoPago(d.getEstadoPago().name())
                .notas(d.getNotas())
                .build();
    }
}
