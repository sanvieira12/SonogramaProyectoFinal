package com.sonograma.service;

import com.sonograma.dto.EnvioDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.dto.VentasPorMesDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Envio;
import com.sonograma.entity.Venta;
import com.sonograma.enums.CanalVenta;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.enums.TipoEntrega;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final EnvioRepository envioRepository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;

    public VentaResponseDTO registrarVenta(VentaRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", dto.getIdCliente()));

        Disco disco = discoRepository.findById(dto.getIdDisco())
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", dto.getIdDisco()));

        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("El disco ya fue vendido");
        }
        if (disco.getEstado() == EstadoDisco.DESCONTINUADO) {
            throw new NegocioException("El disco está descontinuado y no puede venderse");
        }

        Venta venta = Venta.builder()
                .cliente(cliente)
                .disco(disco)
                .fechaVenta(dto.getFechaVenta() != null ? dto.getFechaVenta() : LocalDateTime.now())
                .canalVenta(CanalVenta.valueOf(dto.getCanalVenta()))
                .total(dto.getTotal())
                .tipoEntrega(TipoEntrega.valueOf(dto.getTipoEntrega()))
                .estado(EstadoVenta.COMPLETADA)
                .observaciones(dto.getObservaciones())
                .build();

        venta = ventaRepository.save(venta);

        Envio envio = null;
        if (TipoEntrega.ENVIO.name().equals(dto.getTipoEntrega())) {
            String direccionCompleta = (dto.getDireccionEnvio() != null ? dto.getDireccionEnvio() : "") +
                    (dto.getDepartamento() != null ? ", " + dto.getDepartamento() : "");
            envio = Envio.builder()
                    .venta(venta)
                    .direccionEnvio(direccionCompleta.trim())
                    .estadoLogistico("PREPARANDO")
                    .build();
            envio = envioRepository.save(envio);
        }

        disco.setEstado(EstadoDisco.VENDIDO);
        discoRepository.save(disco);

        return mapearADTO(venta, envio);
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
        return ventaRepository.obtenerVentasAgrupadasPorMes().stream()
                .map(row -> VentasPorMesDTO.builder()
                        .mes((String) row[0])
                        .etiqueta((String) row[1])
                        .cantidad(((Number) row[2]).longValue())
                        .totalMonto(new BigDecimal(row[3].toString()))
                        .build())
                .collect(Collectors.toList());
    }

    private VentaResponseDTO mapearADTO(Venta venta, Envio envio) {
        EnvioDTO envioDTO = null;
        if (envio != null) {
            envioDTO = EnvioDTO.builder()
                    .idEnvio(envio.getIdEnvio())
                    .direccionEnvio(envio.getDireccionEnvio())
                    .estadoLogistico(envio.getEstadoLogistico())
                    .numeroSeguimiento(envio.getNumeroSeguimiento())
                    .fechaEnvio(envio.getFechaEnvio())
                    .fechaEntrega(envio.getFechaEntrega())
                    .build();
        }

        return VentaResponseDTO.builder()
                .idVenta(venta.getIdVenta())
                .idCliente(venta.getCliente().getIdCliente())
                .nombreCliente(venta.getCliente().getNombre())
                .apellidoCliente(venta.getCliente().getApellido())
                .idDisco(venta.getDisco().getIdDisco())
                .artista(venta.getDisco().getArtista())
                .album(venta.getDisco().getAlbum())
                .fechaVenta(venta.getFechaVenta())
                .canalVenta(venta.getCanalVenta() != null ? venta.getCanalVenta().name() : null)
                .total(venta.getTotal())
                .tipoEntrega(venta.getTipoEntrega() != null ? venta.getTipoEntrega().name() : null)
                .estado(venta.getEstado() != null ? venta.getEstado().name() : null)
                .observaciones(venta.getObservaciones())
                .envio(envioDTO)
                .build();
    }
}
