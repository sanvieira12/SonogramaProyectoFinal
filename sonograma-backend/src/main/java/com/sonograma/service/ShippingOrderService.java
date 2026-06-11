package com.sonograma.service;

import com.sonograma.dto.ShippingOrderItemDTO;
import com.sonograma.dto.ShippingOrderRequestDTO;
import com.sonograma.dto.ShippingOrderResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.ShippingOrder;
import com.sonograma.entity.ShippingOrderItem;
import com.sonograma.enums.EstadoShippingOrder;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.ShippingOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShippingOrderService {

    private final ShippingOrderRepository shippingOrderRepository;
    private final DiscoRepository discoRepository;
    private final ExcelExportService excelExportService;

    @Transactional(readOnly = true)
    public List<ShippingOrderResponseDTO> obtenerTodas() {
        return shippingOrderRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShippingOrderResponseDTO obtenerPorId(Long id) {
        return toDTO(shippingOrderRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("ShippingOrder", id)));
    }

    public ShippingOrderResponseDTO crear(ShippingOrderRequestDTO dto) {
        int anio = dto.getFechaOrden() != null ? dto.getFechaOrden().getYear() : LocalDate.now().getYear();
        long count = shippingOrderRepository.countByAnio(anio) + 1;
        String numero = String.format("SO-%d-%03d", anio, count);

        ShippingOrder order = ShippingOrder.builder()
                .numero(numero)
                .proveedor(dto.getProveedor() != null ? dto.getProveedor() : "Vinyl Future")
                .fechaOrden(dto.getFechaOrden() != null ? dto.getFechaOrden() : LocalDate.now())
                .estado(EstadoShippingOrder.PENDIENTE)
                .notas(dto.getNotas())
                .fechaCreacion(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        if (dto.getItems() != null) {
            for (ShippingOrderItemDTO itemDto : dto.getItems()) {
                ShippingOrderItem item = buildItem(itemDto, order);
                order.getItems().add(item);
            }
        }

        BigDecimal costoTotal = order.getItems().stream()
                .map(i -> i.getSubtotal() != null ? i.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setCostoTotal(costoTotal);

        return toDTO(shippingOrderRepository.save(order));
    }

    public ShippingOrder crearDesdeImport(List<com.sonograma.entity.Disco> discos) {
        int anio = LocalDate.now().getYear();
        long count = shippingOrderRepository.countByAnio(anio) + 1;
        String numero = String.format("SO-%d-%03d", anio, count);

        ShippingOrder order = ShippingOrder.builder()
                .numero(numero)
                .proveedor("Vinyl Future")
                .fechaOrden(LocalDate.now())
                .estado(EstadoShippingOrder.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        for (com.sonograma.entity.Disco disco : discos) {
            ShippingOrderItem item = ShippingOrderItem.builder()
                    .shippingOrder(order)
                    .disco(disco)
                    .artista(disco.getArtista())
                    .album(disco.getAlbum())
                    .cantidad(1)
                    .precioUnitario(BigDecimal.ZERO)
                    .subtotal(BigDecimal.ZERO)
                    .build();
            order.getItems().add(item);
        }

        order.setCostoTotal(BigDecimal.ZERO);
        return shippingOrderRepository.save(order);
    }

    public byte[] exportarExcel(Long id) {
        ShippingOrder order = shippingOrderRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("ShippingOrder", id));
        return excelExportService.exportarShippingOrder(order);
    }

    private ShippingOrderItem buildItem(ShippingOrderItemDTO dto, ShippingOrder order) {
        Disco disco = null;
        String artista = dto.getArtista();
        String album = dto.getAlbum();

        if (dto.getIdDisco() != null) {
            disco = discoRepository.findById(dto.getIdDisco()).orElse(null);
            if (disco != null) {
                artista = disco.getArtista();
                album = disco.getAlbum();
            }
        }

        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 1;
        BigDecimal precioUnit = dto.getPrecioUnitario() != null ? dto.getPrecioUnitario() : BigDecimal.ZERO;
        BigDecimal subtotal = dto.getSubtotal() != null ? dto.getSubtotal()
                : precioUnit.multiply(BigDecimal.valueOf(cantidad));

        return ShippingOrderItem.builder()
                .shippingOrder(order)
                .disco(disco)
                .artista(artista)
                .album(album)
                .descripcion(dto.getDescripcion())
                .cantidad(cantidad)
                .precioUnitario(precioUnit)
                .subtotal(subtotal)
                .build();
    }

    private ShippingOrderResponseDTO toDTO(ShippingOrder o) {
        List<ShippingOrderItemDTO> items = o.getItems().stream()
                .map(i -> ShippingOrderItemDTO.builder()
                        .idShippingOrderItem(i.getIdShippingOrderItem())
                        .idDisco(i.getDisco() != null ? i.getDisco().getIdDisco() : null)
                        .artista(i.getArtista())
                        .album(i.getAlbum())
                        .descripcion(i.getDescripcion())
                        .cantidad(i.getCantidad())
                        .precioUnitario(i.getPrecioUnitario())
                        .subtotal(i.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return ShippingOrderResponseDTO.builder()
                .idShippingOrder(o.getIdShippingOrder())
                .numero(o.getNumero())
                .proveedor(o.getProveedor())
                .fechaOrden(o.getFechaOrden())
                .estado(o.getEstado().name())
                .costoTotal(o.getCostoTotal())
                .subtotal(o.getSubtotal())
                .impuestos(o.getImpuestos())
                .otrosCostos(o.getOtrosCostos())
                .totalEstimado(o.getTotalEstimado())
                .notas(o.getNotas())
                .fechaCreacion(o.getFechaCreacion())
                .items(items)
                .build();
    }
}
