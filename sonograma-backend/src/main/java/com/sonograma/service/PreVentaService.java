package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PreVenta;
import com.sonograma.entity.Venta;
import com.sonograma.entity.DetalleVenta;
import com.sonograma.enums.*;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DiscoRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PreVentaService {

    private final PreVentaRepository repository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;

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
            .subtotal(preVenta.getPrecio()).descuentoPorcentaje(BigDecimal.ZERO)
            .costoEnvio(BigDecimal.ZERO).otrosCostos(BigDecimal.ZERO).montoImpuesto(BigDecimal.ZERO)
            .gananciaEstimada(BigDecimal.ZERO).tipoEntrega(TipoEntrega.RETIRO)
            .estado(EstadoVenta.COMPLETADA).observaciones("Cobro de pre-venta #" + id)
            .numeroFactura("PV-" + id).clienteNombreSnapshot(clienteNombre)
            .medioPago(MedioPago.OTRO).montoPagado(preVenta.getPrecio()).montoDeuda(BigDecimal.ZERO)
            .estadoPago(EstadoPago.PAGADO).origen("PRE_VENTA").idPreVentaOrigen(id).build();
        venta = ventaRepository.saveAndFlush(venta);

        BigDecimal unitario = preVenta.getPrecio().divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
        DetalleVenta detalle = DetalleVenta.builder().venta(venta).disco(preVenta.getDisco())
            .precioUnitario(unitario).cantidad(cantidad).manualItem(preVenta.getDisco() == null)
            .artistaSnap(preVenta.getDisco() != null ? preVenta.getDisco().getArtista() : preVenta.getArtistaSnap())
            .albumSnap(preVenta.getDisco() != null ? preVenta.getDisco().getAlbum() : preVenta.getAlbumSnap())
            .descripcionSnap(preVenta.getDescripcionSnap()).codigoSnap(preVenta.getCodigoDisco()).build();
        venta.getDetalles().add(detalleVentaRepository.save(detalle));

        preVenta.setEstado("PAGADA");
        preVenta.setFechaPago(fechaPago);
        preVenta.setVentaPago(venta);
        return toDto(repository.save(preVenta));
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
