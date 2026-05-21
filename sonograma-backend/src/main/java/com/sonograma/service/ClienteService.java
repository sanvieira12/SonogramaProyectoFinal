package com.sonograma.service;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.dto.ClienteDetalleDTO;
import com.sonograma.dto.ClienteRequest;
import com.sonograma.dto.DireccionClienteDTO;
import com.sonograma.dto.EnvioDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.DireccionCliente;
import com.sonograma.entity.Envio;
import com.sonograma.entity.Venta;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.ClienteMapper;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DireccionClienteRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final EnvioRepository envioRepository;
    private final DireccionClienteRepository direccionClienteRepository;

    public ClienteDTO crearCliente(ClienteRequest request) {
        limpiarCamposVacios(request);
        if (request.getCedula() != null && clienteRepository.existsByCedula(request.getCedula())) {
            throw new NegocioException("Ya existe un cliente con la cédula: " + request.getCedula());
        }
        Cliente cliente = ClienteMapper.toEntity(request);
        cliente.setActivo(true);
        cliente = clienteRepository.save(cliente);
        if (request.getDireccion() != null) {
            guardarDireccion(cliente, request.getDireccion(), null, null, false);
        }
        return ClienteMapper.toDTO(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .map(ClienteMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorCedula(String cedula) {
        return clienteRepository.findByCedulaAndActivoTrue(cedula)
                .map(ClienteMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado con cédula: " + cedula));
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> buscar(String q) {
        String query = normalizar(q);
        if (query.isBlank()) {
            return obtenerTodos();
        }
        return clienteRepository.findByActivoTrue().stream()
                .filter(c -> coincideCliente(c, query))
                .map(ClienteMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> obtenerTodos() {
        return clienteRepository.findByActivoTrue().stream()
                .map(ClienteMapper::toDTO)
                .collect(Collectors.toList());
    }

    public ClienteDTO actualizarCliente(Long id, ClienteRequest request) {
        limpiarCamposVacios(request);
        Cliente cliente = clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));

        if (request.getCedula() != null
                && !request.getCedula().equals(cliente.getCedula())
                && clienteRepository.existsByCedulaAndIdClienteNot(request.getCedula(), id)) {
            throw new NegocioException("Ya existe un cliente con la cédula: " + request.getCedula());
        }

        ClienteMapper.updateFromRequest(cliente, request);
        cliente = clienteRepository.save(cliente);
        if (request.getDireccion() != null) {
            guardarDireccion(cliente, request.getDireccion(), null, null, false);
        }
        return ClienteMapper.toDTO(cliente);
    }

    public void eliminarCliente(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteDetalleDTO obtenerDetalle(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));

        List<Venta> ventas = ventaRepository.findByClienteIdClienteOrderByFechaVentaDesc(id);
        List<VentaResponseDTO> historialCompras = ventas.stream()
                .map(this::mapearVenta)
                .collect(Collectors.toList());
        List<DireccionClienteDTO> direcciones = obtenerDirecciones(id);
        List<EnvioDTO> envios = envioRepository.findByVentaClienteIdClienteOrderByFechaEnvioDesc(id).stream()
                .map(this::mapearEnvio)
                .collect(Collectors.toList());

        BigDecimal totalGastado = ventas.stream()
                .map(this::totalVenta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = ventas.isEmpty()
                ? BigDecimal.ZERO
                : totalGastado.divide(BigDecimal.valueOf(ventas.size()), 2, RoundingMode.HALF_UP);
        BigDecimal mayorCompra = ventas.stream()
                .map(this::totalVenta)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return ClienteDetalleDTO.builder()
                .cliente(ClienteMapper.toDTO(cliente))
                .historialCompras(historialCompras)
                .direcciones(direcciones)
                .historialEnvios(envios)
                .cantidadTotalCompras((long) ventas.size())
                .dineroTotalGastado(totalGastado)
                .promedioGastadoPorCompra(promedio)
                .mayorGastoCompraIndividual(mayorCompra)
                .generoMasComprado(masFrecuente(ventas, v -> texto(v.getDisco().getGenero(), "Sin género")))
                .decadaMusicalMasComprada(masFrecuente(ventas, v -> decada(v.getDisco().getAnio())))
                .mesMasCompras(masFrecuente(ventas, v -> mes(v.getFechaVenta() != null ? v.getFechaVenta().getMonth() : null)))
                .ultimaCompra(ventas.stream()
                        .map(Venta::getFechaVenta)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null))
                .build();
    }

    @Transactional(readOnly = true)
    public List<DireccionClienteDTO> obtenerDirecciones(Long idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", idCliente));

        List<DireccionClienteDTO> direcciones = direccionClienteRepository
                .findByClienteIdClienteAndActivaTrueOrderByUltimaUsadaDescFechaAltaDesc(idCliente)
                .stream()
                .map(this::mapearDireccion)
                .collect(Collectors.toList());

        if (direcciones.isEmpty() && cliente.getDireccion() != null && !cliente.getDireccion().isBlank()) {
            direcciones.add(DireccionClienteDTO.builder()
                    .direccion(cliente.getDireccion())
                    .build());
        }
        return direcciones;
    }

    public DireccionClienteDTO crearDireccion(Long idCliente, DireccionClienteDTO dto) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", idCliente));

        DireccionCliente direccion = guardarDireccion(
                cliente,
                dto.getDireccion(),
                dto.getDepartamento(),
                dto.getReferencia(),
                true
        );
        return mapearDireccion(direccion);
    }

    public DireccionCliente guardarDireccion(
            Cliente cliente,
            String direccionTexto,
            String departamento,
            String referencia,
            boolean marcarUsada
    ) {
        if (direccionTexto == null || direccionTexto.isBlank()) {
            throw new NegocioException("La dirección es obligatoria");
        }

        String direccionNormalizada = normalizar(direccionTexto);
        String departamentoNormalizado = normalizar(departamento);
        DireccionCliente existente = direccionClienteRepository
                .findByClienteIdClienteAndActivaTrueOrderByUltimaUsadaDescFechaAltaDesc(cliente.getIdCliente())
                .stream()
                .filter(d -> normalizar(d.getDireccion()).equals(direccionNormalizada)
                        && normalizar(d.getDepartamento()).equals(departamentoNormalizado))
                .findFirst()
                .orElse(null);

        DireccionCliente direccion = existente != null
                ? existente
                : DireccionCliente.builder()
                    .cliente(cliente)
                    .direccion(direccionTexto.trim())
                    .departamento(textoNulo(departamento))
                    .referencia(textoNulo(referencia))
                    .build();

        if (marcarUsada) {
            direccion.setUltimaUsada(java.time.LocalDateTime.now());
        }
        return direccionClienteRepository.save(direccion);
    }

    private boolean coincideCliente(Cliente cliente, String query) {
        boolean coincideDatos = contiene(cliente.getNombre(), query)
                || contiene(cliente.getApellido(), query)
                || contiene(cliente.getNombre() + " " + texto(cliente.getApellido(), ""), query)
                || contiene(cliente.getCedula(), query)
                || contiene(cliente.getInstagramUsuario(), query)
                || contiene(cliente.getDireccion(), query)
                || contiene(cliente.getTelefono(), query)
                || contiene(cliente.getEmail(), query);
        if (coincideDatos) return true;

        boolean coincideDireccion = direccionClienteRepository
                .findByClienteIdClienteAndActivaTrueOrderByUltimaUsadaDescFechaAltaDesc(cliente.getIdCliente())
                .stream()
                .anyMatch(d -> contiene(d.getDireccion(), query) || contiene(d.getDepartamento(), query));
        if (coincideDireccion) return true;

        return ventaRepository.findByClienteIdClienteOrderByFechaVentaDesc(cliente.getIdCliente()).stream()
                .anyMatch(v -> contiene(v.getDisco().getAlbum(), query)
                        || contiene(v.getDisco().getArtista(), query)
                        || contiene(v.getDisco().getGenero(), query)
                        || contiene(v.getDisco().getSelloDiscografico(), query));
    }

    private VentaResponseDTO mapearVenta(Venta venta) {
        Envio envio = envioRepository.findByVentaIdVenta(venta.getIdVenta()).orElse(null);
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
                .envio(envio != null ? mapearEnvio(envio) : null)
                .build();
    }

    private EnvioDTO mapearEnvio(Envio envio) {
        return EnvioDTO.builder()
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

    private DireccionClienteDTO mapearDireccion(DireccionCliente direccion) {
        return DireccionClienteDTO.builder()
                .idDireccion(direccion.getIdDireccion())
                .direccion(direccion.getDireccion())
                .departamento(direccion.getDepartamento())
                .referencia(direccion.getReferencia())
                .fechaAlta(direccion.getFechaAlta())
                .ultimaUsada(direccion.getUltimaUsada())
                .build();
    }

    private BigDecimal totalVenta(Venta venta) {
        if (venta.getTotalFinal() != null) return venta.getTotalFinal();
        return venta.getTotal() != null ? venta.getTotal() : BigDecimal.ZERO;
    }

    private String masFrecuente(List<Venta> ventas, Function<Venta, String> extractor) {
        return ventas.stream()
                .map(extractor)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String decada(Integer anio) {
        if (anio == null) return null;
        return (anio / 10 * 10) + "s";
    }

    private String mes(Month month) {
        if (month == null) return null;
        return month.getDisplayName(TextStyle.FULL, new Locale("es", "UY"));
    }

    private void limpiarCamposVacios(ClienteRequest request) {
        request.setCedula(textoNulo(request.getCedula()));
        request.setTelefono(textoNulo(request.getTelefono()));
        request.setEmail(textoNulo(request.getEmail()));
        request.setInstagramUsuario(textoNulo(request.getInstagramUsuario()));
        request.setDireccion(textoNulo(request.getDireccion()));
    }

    private boolean contiene(String valor, String query) {
        return valor != null && normalizar(valor).contains(query);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor.trim();
    }

    private String textoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
