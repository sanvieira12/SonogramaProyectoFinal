package com.sonograma.service;

import com.sonograma.dto.EnvioDTO;
import com.sonograma.dto.ConfiguracionCostosDTO;
import com.sonograma.dto.ResultadoCostoVentaDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.dto.VentasPorMesDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DireccionCliente;
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
import com.sonograma.repository.DireccionClienteRepository;
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
    private final DireccionClienteRepository direccionClienteRepository;
    private final ClienteService clienteService;
    private final CostosVentaService costosVentaService;

    public VentaResponseDTO registrarVenta(VentaRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(dto.getIdCliente())
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", dto.getIdCliente()));

        Disco disco = discoRepository.findById(dto.getIdDisco())
                .orElseThrow(() -> new RecursoNoEncontradoException("Disco", dto.getIdDisco()));

        if (disco.getEstado() == EstadoDisco.VENDIDO) {
            throw new NegocioException("El disco ya fue vendido");
        }
        if (disco.getEstado() == EstadoDisco.FUERA_STOCK) {
            throw new NegocioException("El disco está fuera de stock");
        }
        if (disco.getEstado() == EstadoDisco.DESCONTINUADO) {
            throw new NegocioException("El disco está descontinuado y no puede venderse");
        }

        CanalVenta canal = parseCanal(dto.getCanalVenta());
        TipoEntrega entrega = parseEntrega(dto.getTipoEntrega());
        ResultadoCostoVentaDTO costos = costosVentaService.calcular(disco, dto);

        Venta venta = Venta.builder()
                .cliente(cliente)
                .disco(disco)
                .fechaVenta(dto.getFechaVenta() != null ? dto.getFechaVenta() : LocalDateTime.now())
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
                .tipoEntrega(entrega)
                .estado(EstadoVenta.COMPLETADA)
                .observaciones(dto.getObservaciones())
                .build();

        venta = ventaRepository.save(venta);

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

    @Transactional(readOnly = true)
    public ConfiguracionCostosDTO obtenerConfiguracionCostos() {
        return costosVentaService.obtenerConfiguracion();
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
                .costoDisco(venta.getCostoDisco())
                .precioVenta(venta.getPrecioVenta())
                .costoEnvio(venta.getCostoEnvio())
                .porcentajeImpuesto(venta.getPorcentajeImpuesto())
                .montoImpuesto(venta.getMontoImpuesto())
                .otrosCostos(venta.getOtrosCostos())
                .totalFinal(venta.getTotalFinal())
                .gananciaEstimada(venta.getGananciaEstimada())
                .tipoEntrega(venta.getTipoEntrega() != null ? venta.getTipoEntrega().name() : null)
                .estado(venta.getEstado() != null ? venta.getEstado().name() : null)
                .observaciones(venta.getObservaciones())
                .envio(envioDTO)
                .build();
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

        return clienteService.guardarDireccion(
                cliente,
                dto.getDireccionEnvio(),
                dto.getDepartamento(),
                null,
                true
        );
    }

    private String armarDireccionEnvio(DireccionCliente direccionCliente, VentaRequestDTO dto) {
        String direccion = direccionCliente != null ? direccionCliente.getDireccion() : dto.getDireccionEnvio();
        if (direccion == null || direccion.isBlank()) {
            throw new NegocioException("Ingresá la dirección de envío");
        }
        String departamento = dto.getDepartamento() != null ? dto.getDepartamento().trim() : null;
        String sucursal = dto.getSucursalDacNombre() != null ? dto.getSucursalDacNombre().trim() : null;

        StringBuilder sb = new StringBuilder(direccion.trim());
        if (departamento != null && !departamento.isBlank()) {
            sb.append(", ").append(departamento);
        }
        if (sucursal != null && !sucursal.isBlank()) {
            sb.append(" - DAC ").append(sucursal);
        }
        return sb.toString();
    }

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
}
