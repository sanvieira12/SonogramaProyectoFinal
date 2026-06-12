package com.sonograma.service;

import com.sonograma.dto.*;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.*;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PedidoItemRepository;
import com.sonograma.repository.PedidoRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PdfInvoiceParser pdfParser;
    private final PedidoEnrichmentService enrichmentService;
    private final DiscoRepository discoRepository;
    private final AudioPreviewService audioPreviewService;

    private final ExecutorService enrichPool = Executors.newFixedThreadPool(3);

    // ── Upload ────────────────────────────────────────────────────────────────

    public PedidoUploadResponseDTO crearDesdePdf(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el PDF: " + e.getMessage());
        }

        ParsedInvoice invoice;
        try {
            invoice = pdfParser.parseInvoice(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al parsear el PDF: " + e.getMessage());
        }

        Pedido pedido = Pedido.builder()
            .numeroFactura(invoice.numeroFactura())
            .fechaFactura(invoice.fechaFactura())
            .proveedor(invoice.proveedor() != null ? invoice.proveedor() : "Vinyl Future")
            .pago(invoice.pago())
            .moneda(invoice.moneda() != null ? invoice.moneda() : "EUR")
            .pesoTotalKg(invoice.pesoTotalKg())
            .terminosVenta(invoice.terminosVenta())
            .codigoArancel(invoice.codigoArancel())
            .eoriNo(invoice.eoriNo())
            .nombreArchivo(file.getOriginalFilename())
            .textoExtraido(invoice.rawExtractText())
            .franqueo(invoice.franqueo())
            .tarifas(invoice.tarifas())
            .neto(invoice.neto())
            .total(invoice.total())
            .cantidadTotalPdf(invoice.cantidadTotalPdf())
            .importStatus(ImportStatus.PARSED)
            .build();

        pedido = pedidoRepository.save(pedido);

        List<PedidoItem> items = new ArrayList<>();
        for (InvoiceItem inv : invoice.items()) {
            PedidoItem item = PedidoItem.builder()
                .pedido(pedido)
                .codigo(inv.codigoCatalogo())
                .artista(inv.artista())
                .titulo(inv.album())
                .formato(inv.formato())
                .precioUnitarioEur(inv.precioUnitario())
                .cantidad(inv.cantidad())
                .totalLineaEur(calcLineTotal(inv))
                .enrichStatus(EnrichStatus.PENDING)
                .build();
            items.add(item);
        }
        pedidoItemRepository.saveAll(items);

        int sumCantidad = items.stream().mapToInt(i -> i.getCantidad() != null ? i.getCantidad() : 0).sum();
        boolean advertencia = invoice.cantidadTotalPdf() != null && invoice.cantidadTotalPdf() != sumCantidad;

        log.info("Pedido {} creado: {} ítems, total={}", pedido.getIdPedido(), items.size(), invoice.total());
        return new PedidoUploadResponseDTO(
            pedido.getIdPedido(),
            pedido.getNumeroFactura(),
            items.size(),
            invoice.cantidadTotalPdf(),
            advertencia
        );
    }

    private BigDecimal calcLineTotal(InvoiceItem inv) {
        if (inv.subtotal() != null) return inv.subtotal();
        if (inv.precioUnitario() != null && inv.cantidad() != null) {
            return inv.precioUnitario().multiply(BigDecimal.valueOf(inv.cantidad()));
        }
        return null;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listar() {
        return pedidoRepository.findAllOrderedByCreatedAt().stream()
            .map(p -> toDTO(p, false))
            .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        return toDTO(pedido, true);
    }

    // ── Settings + Recalc ─────────────────────────────────────────────────────

    public PedidoResponseDTO actualizarConfiguracion(Long id, PedidoConfiguracionDTO cfg) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));

        if (cfg.tipoCambio() != null)       pedido.setTipoCambio(cfg.tipoCambio());
        if (cfg.extraCostoSimple() != null)  pedido.setExtraCostoSimple(cfg.extraCostoSimple());
        if (cfg.extraCostoDoble() != null)   pedido.setExtraCostoDoble(cfg.extraCostoDoble());
        if (cfg.markupSimple() != null)      pedido.setMarkupSimple(cfg.markupSimple());
        if (cfg.markupDoble() != null)       pedido.setMarkupDoble(cfg.markupDoble());

        pedidoRepository.save(pedido);
        recalcularItems(pedido);
        return toDTO(pedido, true);
    }

    private void recalcularItems(Pedido pedido) {
        for (PedidoItem item : pedido.getItems()) {
            calcularItem(item, pedido);
            pedidoItemRepository.save(item);
        }
    }

    private void calcularItem(PedidoItem item, Pedido pedido) {
        boolean esDoble = "Double".equalsIgnoreCase(item.getFormato());

        BigDecimal extraCosto = esDoble ? pedido.getExtraCostoDoble() : pedido.getExtraCostoSimple();
        BigDecimal markup    = esDoble ? pedido.getMarkupDoble()     : pedido.getMarkupSimple();

        item.setExtraCostoEur(extraCosto);

        BigDecimal unitPrice = item.getPrecioUnitarioEur();
        if (unitPrice != null && extraCosto != null) {
            BigDecimal costoRealEur = unitPrice.add(extraCosto);
            item.setCostoRealEur(costoRealEur);

            if (pedido.getTipoCambio() != null) {
                BigDecimal costoRealUyu = costoRealEur.multiply(pedido.getTipoCambio())
                    .setScale(2, RoundingMode.HALF_UP);
                item.setCostoRealUyu(costoRealUyu);

                if (markup != null) {
                    item.setMarkup(markup);
                    BigDecimal precioFinal = costoRealUyu.multiply(markup)
                        .setScale(2, RoundingMode.HALF_UP);
                    item.setPrecioFinalUyu(precioFinal);
                }
            }
        }
    }

    // ── Enrichment ────────────────────────────────────────────────────────────

    public void lanzarEnriquecimiento(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", pedidoId));

        pedido.setImportStatus(ImportStatus.ENRICHING);
        pedidoRepository.save(pedido);

        List<Long> itemIds = pedidoItemRepository
            .findByPedidoIdPedidoAndEnrichStatusIn(
                pedidoId,
                List.of(EnrichStatus.PENDING, EnrichStatus.FAILED))
            .stream()
            .map(PedidoItem::getIdPedidoItem)
            .toList();

        if (itemIds.isEmpty()) {
            pedido.setImportStatus(ImportStatus.AWAITING_REVIEW);
            pedidoRepository.save(pedido);
            return;
        }

        CompletableFuture.runAsync(() -> {
            Semaphore sem = new Semaphore(3);
            List<CompletableFuture<Void>> futures = itemIds.stream()
                .map(itemId -> CompletableFuture.runAsync(() -> {
                    try {
                        sem.acquire();
                        enrichmentService.procesarItem(itemId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        sem.release();
                    }
                }, enrichPool))
                .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            enrichmentService.marcarPedidoPostEnriquecimiento(pedidoId, pedidoRepository);
            log.info("Enriquecimiento completado para pedido {}", pedidoId);
        }, enrichPool);

        log.info("Enriquecimiento lanzado en background para pedido {} ({} ítems)", pedidoId, itemIds.size());
    }

    public void reintentarItemEnriquecimiento(Long itemId) {
        PedidoItem item = pedidoItemRepository.findById(itemId)
            .orElseThrow(() -> new RecursoNoEncontradoException("PedidoItem", itemId));
        item.setEnrichStatus(EnrichStatus.PENDING);
        pedidoItemRepository.save(item);
        CompletableFuture.runAsync(() -> enrichmentService.procesarItem(itemId), enrichPool);
    }

    // ── Import to catalog ─────────────────────────────────────────────────────

    public PedidoResponseDTO importarAlCatalogo(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", pedidoId));

        pedido.setImportStatus(ImportStatus.IMPORTING_TO_CATALOG);
        pedidoRepository.save(pedido);

        int ok = 0, failed = 0;
        for (PedidoItem item : pedido.getItems()) {
            try {
                Disco disco = upsertDisco(item, pedido);
                item.setDisco(disco);
                item.setEnrichStatus(EnrichStatus.IMPORTED);
                pedidoItemRepository.save(item);
                // Persist scraped audio previews linked to the catalog product
                try {
                    var tracks = enrichmentService.deserializarTracks(item.getTracksJson());
                    audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), tracks);
                } catch (Exception ex) {
                    log.warn("No se pudieron guardar previews para item {}: {}", item.getIdPedidoItem(), ex.getMessage());
                }
                ok++;
            } catch (Exception e) {
                log.warn("Error importando item {} al catálogo: {}", item.getIdPedidoItem(), e.getMessage());
                failed++;
            }
        }

        pedido.setImportStatus(failed == 0 ? ImportStatus.COMPLETED : ImportStatus.PARTIALLY_COMPLETED);
        pedidoRepository.save(pedido);
        log.info("Pedido {}: {} importados, {} fallidos", pedidoId, ok, failed);
        return toDTO(pedido, true);
    }

    private Disco upsertDisco(PedidoItem item, Pedido pedido) {
        Disco disco = item.getCodigo() != null && !item.getCodigo().isBlank()
            ? discoRepository.findByCodigoInterno(item.getCodigo()).orElse(null)
            : null;

        if (disco == null) {
            disco = new Disco();
            disco.setCodigoQr(UUID.randomUUID().toString());
        }

        disco.setCodigoInterno(item.getCodigo());
        disco.setArtista(item.getArtista() != null ? item.getArtista() : "Desconocido");
        disco.setAlbum(item.getTitulo() != null ? item.getTitulo() : "Sin título");
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setProcedencia("IMPORTADO");
        disco.setTipoDisco(TipoDisco.VINILO);
        disco.setCondicion(CondicionDisco.NUEVO);
        disco.setCantidadCopias(item.getCantidad() != null ? item.getCantidad() : 1);
        disco.setCosto(item.getCostoRealEur() != null ? item.getCostoRealEur() : item.getPrecioUnitarioEur());
        if (item.getPrecioFinalUyu() != null) disco.setPrecioVenta(item.getPrecioFinalUyu());
        if (item.getPortadaUrl() != null)     disco.setImagenUrl(item.getPortadaUrl());

        return discoRepository.save(disco);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private PedidoResponseDTO toDTO(Pedido p, boolean includeItems) {
        List<PedidoItem> items = includeItems ? p.getItems() : List.of();
        List<PedidoItemResponseDTO> itemDTOs = items.stream().map(this::toItemDTO).toList();

        BigDecimal merchandiseTotal = items.stream()
            .filter(i -> i.getTotalLineaEur() != null)
            .map(PedidoItem::getTotalLineaEur)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int sumCantidad = items.stream().mapToInt(i -> i.getCantidad() != null ? i.getCantidad() : 0).sum();
        boolean advertencia = p.getCantidadTotalPdf() != null && p.getCantidadTotalPdf() != sumCantidad;

        return new PedidoResponseDTO(
            p.getIdPedido(),
            p.getNumeroFactura(),
            p.getFechaFactura(),
            p.getProveedor(),
            p.getPago(),
            p.getMoneda(),
            p.getPesoTotalKg(),
            p.getTerminosVenta(),
            p.getCodigoArancel(),
            p.getEoriNo(),
            p.getNombreArchivo(),
            includeItems ? p.getTextoExtraido() : null,
            p.getFranqueo(),
            p.getTarifas(),
            p.getNeto(),
            p.getTotal(),
            p.getCantidadTotalPdf(),
            p.getImportStatus().name(),
            p.getTipoCambio(),
            p.getExtraCostoSimple(),
            p.getExtraCostoDoble(),
            p.getMarkupSimple(),
            p.getMarkupDoble(),
            p.getCreatedAt(),
            itemDTOs,
            merchandiseTotal,
            items.size(),
            sumCantidad,
            advertencia
        );
    }

    private PedidoItemResponseDTO toItemDTO(PedidoItem i) {
        return new PedidoItemResponseDTO(
            i.getIdPedidoItem(),
            i.getCodigo(),
            i.getArtista(),
            i.getTitulo(),
            i.getFormato(),
            i.getPrecioUnitarioEur(),
            i.getCantidad(),
            i.getTotalLineaEur(),
            i.getExtraCostoEur(),
            i.getCostoRealEur(),
            i.getCostoRealUyu(),
            i.getMarkup(),
            i.getPrecioFinalUyu(),
            i.getPortadaUrl(),
            i.getDisco() != null ? i.getDisco().getIdDisco() : null,
            i.getEnrichStatus() != null ? i.getEnrichStatus().name() : null
        );
    }

    @PreDestroy
    void shutdown() {
        enrichPool.shutdown();
    }
}
