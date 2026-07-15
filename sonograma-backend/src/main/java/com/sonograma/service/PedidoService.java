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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final DiscoQrCopyService qrCopyService;
    private final CatalogPricingService catalogPricingService;

    private final ExecutorService enrichPool = Executors.newFixedThreadPool(3);

    @Value("${sonograma.pedidos.pdf-directory:./data/pedidos-pdf}")
    private String pdfDirectory;

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
            .proveedor(firstNonBlank(
                ImportMetadataNormalizer.normalizeSource(invoice.proveedor()),
                ImportMetadataNormalizer.SOURCE_FUTURE
            ))
            .envio(ImportMetadataNormalizer.normalizeShipping(
                ImportMetadataNormalizer.normalizeSource(invoice.proveedor()),
                invoice.envio()
            ))
            .pago(invoice.pago())
            .unidadPeso(invoice.unidadPeso())
            .moneda(invoice.moneda() != null ? invoice.moneda() : "EUR")
            .pesoTotalKg(invoice.pesoTotalKg())
            .terminosVenta(invoice.terminosVenta())
            .codigoArancel(invoice.codigoArancel())
            .eoriNo(invoice.eoriNo())
            .destinatario(invoice.destinatario())
            .nombreArchivo(file.getOriginalFilename())
            .pdfOriginalFilename(file.getOriginalFilename())
            .pdfContentType(file.getContentType() != null ? file.getContentType() : "application/pdf")
            .textoExtraido(invoice.rawExtractText())
            .franqueo(invoice.franqueo())
            .tarifas(invoice.tarifas())
            .neto(invoice.neto())
            .iva(invoice.iva())
            .iva7(invoice.iva7())
            .iva19(invoice.iva19())
            .total(invoice.total())
            .cantidadTotalPdf(invoice.cantidadTotalPdf())
            .importStatus(ImportStatus.PARSED)
            .build();

        pedido.setPdfStoragePath(guardarPdfOriginal(file));
        pedido.setPdfUploadedAt(java.time.LocalDateTime.now());

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
            calcularItem(item);
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

    /** Stores the invoice before optional VinylFuture enrichment and catalog work. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pedido persistirVinylFuture(byte[] pdfBytes, String filename, ParsedInvoice invoice) {
        String invoiceNumber = ImportMetadataNormalizer.blankToNull(invoice.numeroFactura());
        if (invoiceNumber == null) {
            throw new IllegalArgumentException("La factura VinylFuture no contiene número de factura");
        }
        if (pedidoRepository.findByOrigenImportacionAndNumeroFactura("vinylfuture", invoiceNumber).isPresent()) {
            throw new IllegalArgumentException("La factura " + invoiceNumber
                + " ya fue importada. Se bloqueó la importación para evitar duplicados.");
        }

        Pedido pedido = Pedido.builder()
            .numeroFactura(invoiceNumber)
            .fechaFactura(invoice.fechaFactura())
            .proveedor("VinylFuture")
            .origenImportacion("vinylfuture")
            .envio(invoice.envio())
            .pago(invoice.pago())
            .unidadPeso(invoice.unidadPeso())
            .moneda(invoice.moneda() != null ? invoice.moneda() : "EUR")
            .pesoTotalKg(invoice.pesoTotalKg())
            .terminosVenta(invoice.terminosVenta())
            .codigoArancel(invoice.codigoArancel())
            .eoriNo(invoice.eoriNo())
            .nombreArchivo(filename)
            .pdfOriginalFilename(filename)
            .pdfContentType("application/pdf")
            .textoExtraido(invoice.rawExtractText())
            .franqueo(invoice.franqueo())
            .tarifas(invoice.tarifas())
            .neto(invoice.neto())
            .iva(invoice.iva())
            .cantidadTotalPdf(invoice.cantidadTotalPdf())
            .importStatus(ImportStatus.PARSED)
            .build();
        pedido.setPdfStoragePath(guardarPdfOriginal(pdfBytes, filename));
        pedido.setPdfUploadedAt(java.time.LocalDateTime.now());
        pedido = pedidoRepository.save(pedido);

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
            calcularItem(item);
            pedido.getItems().add(item);
        }
        pedidoRepository.save(pedido);
        log.info("Pedido VinylFuture {} creado: {} líneas", pedido.getIdPedido(), pedido.getItems().size());
        return pedido;
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
    public List<PedidoResponseDTO> listarVinylFuture() {
        return pedidoRepository.findByOrigenImportacionOrderByCreatedAtDesc("vinylfuture").stream()
            .map(p -> toDTO(p, false))
            .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        return toDTO(pedido, true);
    }

    @Transactional(readOnly = true)
    public Resource obtenerPdfOriginal(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        if (isBlank(pedido.getPdfStoragePath())) {
            throw new RecursoNoEncontradoException("PDF del pedido", id);
        }
        try {
            Resource resource = new UrlResource(Path.of(pedido.getPdfStoragePath()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RecursoNoEncontradoException("PDF del pedido", id);
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw new RecursoNoEncontradoException("PDF del pedido", id);
        }
    }

    @Transactional(readOnly = true)
    public Pedido obtenerEntidad(Long id) {
        return pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
    }

    // ── Settings + Recalc ─────────────────────────────────────────────────────

    public PedidoResponseDTO actualizarConfiguracion(Long id, PedidoConfiguracionDTO cfg) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
        PricingSettingsDTO settings = catalogPricingService.getSettingsDto();

        pedido.setTipoCambio(settings.eurUyuRate());
        pedido.setExtraCostoSimple(settings.extraCostSingleEur());
        pedido.setExtraCostoDoble(settings.extraCostDoubleEur());
        pedido.setMarkupSimple(settings.markupSingle());
        pedido.setMarkupDoble(settings.markupDouble());

        pedidoRepository.save(pedido);
        recalcularItems(pedido);
        return toDTO(pedido, true);
    }

    private void recalcularItems(Pedido pedido) {
        for (PedidoItem item : pedido.getItems()) {
            calcularItem(item);
            pedidoItemRepository.save(item);
        }
    }

    private void calcularItem(PedidoItem item) {
        CatalogPricingService.PricingResult result =
            catalogPricingService.calculate(item.getPrecioUnitarioEur(), item.getFormato());
        if (result == null) return;
        item.setExtraCostoEur(result.extraCostEur());
        item.setCostoRealEur(result.realUnitCostEur());
        item.setCostoRealUyu(result.realUnitCostUyu());
        item.setMarkup(result.markup());
        item.setPrecioFinalUyu(result.finalPriceUyu());
    }

    private boolean esDoble(String formato) {
        return catalogPricingService.esDoble(formato);
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
        if (item.getPrecioFinalUyu() == null || item.getPrecioFinalUyu().compareTo(BigDecimal.ZERO) <= 0) {
            calcularItem(item);
            pedidoItemRepository.save(item);
        }

        VinylPageData scraped = enrichmentService.deserializarPageData(item.getPageDataJson()).orElse(null);
        Disco disco = item.getCodigo() != null && !item.getCodigo().isBlank()
            ? discoRepository.findByCodigoInterno(item.getCodigo()).orElse(null)
            : null;

        if (disco == null) {
            disco = new Disco();
            disco.setCodigoQr(UUID.randomUUID().toString());
        }

        setIfPresent(disco::setCodigoInterno, firstNonBlank(item.getCodigo(), scraped != null ? scraped.code() : null));
        setIfPresent(disco::setArtista, firstNonBlank(item.getArtista(), scraped != null ? scraped.artist() : null));
        setIfPresent(disco::setAlbum, firstNonBlank(item.getTitulo(), scraped != null ? scraped.title() : null));
        if (isBlank(disco.getArtista())) disco.setArtista("Desconocido");
        if (isBlank(disco.getAlbum())) disco.setAlbum("Sin título");
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setProcedencia("IMPORTADO");
        disco.setTipoDisco(mapTipo(firstNonBlank(item.getFormato(), scraped != null ? scraped.format() : null),
            disco.getTipoDisco()));
        disco.setCondicion(mapCondicion(scraped != null ? scraped.condition() : null, disco.getCondicion()));
        disco.setCantidadCopias(item.getCantidad() != null ? item.getCantidad() : 1);
        BigDecimal purchasePrice = firstNonNull(item.getPrecioUnitarioEur(), scraped != null ? scraped.purchasePrice() : null);
        if (purchasePrice != null) disco.setCosto(purchasePrice);
        disco.setCostoMoneda("EUR");
        disco.setFormato(firstNonBlank(item.getFormato(), scraped != null ? scraped.format() : null));
        if (item.getPrecioFinalUyu() != null) disco.setPrecioVenta(item.getPrecioFinalUyu());
        disco.setPricingMode(PricingMode.AUTO);
        setIfPresent(disco::setImagenUrl, firstNonBlank(item.getPortadaUrl(),
            scraped != null ? scraped.frontImageUrl() : null));

        if (scraped != null) {
            setIfMissing(disco.getSelloDiscografico(), disco::setSelloDiscografico, scraped.label());
            setIfMissing(disco.getGenero(), disco::setGenero, scraped.genre());
            setIfMissing(disco.getPais(), disco::setPais, scraped.country());
            setIfMissing(disco.getDescripcion(), disco::setDescripcion, scraped.description());
            if (disco.getAnio() == null && scraped.year() != null) disco.setAnio(scraped.year());
            if (scraped.tracks() != null && !scraped.tracks().isEmpty()) {
                if (isBlank(disco.getTracklist())) {
                    disco.setTracklist(scraped.tracks().stream()
                        .map(t -> String.join(" ", nonBlank(t.label(), t.name())))
                        .filter(s -> !s.isBlank())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse(null));
                }
            }
        }

        disco = discoRepository.save(disco);
        qrCopyService.synchronize(disco);
        return discoRepository.save(disco);
    }

    private TipoDisco mapTipo(String value, TipoDisco existing) {
        if (existing != null) return existing;
        if (isBlank(value)) return existing != null ? existing : TipoDisco.VINILO;
        String normalized = value.toUpperCase();
        if (normalized.contains("CD")) return TipoDisco.CD;
        if (normalized.contains("CASSETTE")) return TipoDisco.CASSETTE;
        if (normalized.contains("DIGITAL")) return TipoDisco.DIGITAL;
        return TipoDisco.VINILO;
    }

    private CondicionDisco mapCondicion(String value, CondicionDisco existing) {
        if (existing != null) return existing;
        if (isBlank(value)) return existing != null ? existing : CondicionDisco.NUEVO;
        String normalized = value.toUpperCase();
        if (normalized.contains("USED") || normalized.contains("USADO") || normalized.startsWith("VG")) {
            return CondicionDisco.USADO;
        }
        return CondicionDisco.NUEVO;
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (!isBlank(value)) setter.accept(value.strip());
    }

    private void setIfMissing(String current, java.util.function.Consumer<String> setter, String fallback) {
        if (isBlank(current)) setIfPresent(setter, fallback);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String[] nonBlank(String... values) {
        return java.util.Arrays.stream(values).filter(v -> !isBlank(v)).toArray(String[]::new);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private PedidoResponseDTO toDTO(Pedido p, boolean includeItems) {
        List<PedidoItem> allItems = p.getItems();
        List<PedidoItemResponseDTO> itemDTOs = includeItems
            ? allItems.stream().map(this::toItemDTO).toList()
            : List.of();

        BigDecimal merchandiseTotal = allItems.stream()
            .filter(i -> i.getTotalLineaEur() != null)
            .map(PedidoItem::getTotalLineaEur)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int sumCantidad = allItems.stream().mapToInt(i -> i.getCantidad() != null ? i.getCantidad() : 0).sum();
        boolean advertencia = p.getCantidadTotalPdf() != null && p.getCantidadTotalPdf() != sumCantidad;

        PricingSettingsDTO settings = catalogPricingService.getSettingsDto();
        return new PedidoResponseDTO(
            p.getIdPedido(),
            p.getNumeroFactura(),
            p.getFechaFactura(),
            p.getProveedor(),
            p.getOrigenImportacion(),
            p.getDestinatario(),
            p.getEnvio(),
            p.getPago(),
            p.getUnidadPeso(),
            p.getMoneda(),
            p.getPesoTotalKg(),
            p.getTerminosVenta(),
            p.getCodigoArancel(),
            p.getEoriNo(),
            p.getNombreArchivo(),
            p.getPdfOriginalFilename(),
            p.getPdfContentType(),
            p.getPdfUploadedAt(),
            !isBlank(p.getPdfStoragePath()),
            includeItems ? p.getTextoExtraido() : null,
            p.getFranqueo(),
            p.getTarifas(),
            p.getNeto(),
            p.getIva(),
            p.getIva7(),
            p.getIva19(),
            p.getTotal(),
            p.getCantidadTotalPdf(),
            p.getImportStatus().name(),
            settings.eurUyuRate(),
            settings.extraCostSingleEur(),
            settings.extraCostDoubleEur(),
            settings.markupSingle(),
            settings.markupDouble(),
            p.getCreatedAt(),
            p.getUpdatedAt(),
            itemDTOs,
            merchandiseTotal,
            allItems.size(),
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
            catalogPricingService.detectRecordType(i.getFormato()).name(),
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

    private String guardarPdfOriginal(MultipartFile file) {
        try {
            Files.createDirectories(Path.of(pdfDirectory));
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "pedido.pdf";
            String extension = original.toLowerCase().endsWith(".pdf") ? ".pdf" : "";
            Path destino = Path.of(pdfDirectory, UUID.randomUUID() + extension).toAbsolutePath().normalize();
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar el PDF original: " + e.getMessage());
        }
    }

    private String guardarPdfOriginal(byte[] bytes, String filename) {
        try {
            Files.createDirectories(Path.of(pdfDirectory));
            String original = filename != null ? filename : "pedido.pdf";
            String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = Path.of(pdfDirectory).resolve(UUID.randomUUID() + "-" + safe).normalize();
            Files.write(target, bytes);
            return target.toString();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar el PDF original: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        enrichPool.shutdown();
    }
}
