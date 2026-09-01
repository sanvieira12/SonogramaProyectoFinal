package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylFutureManualImportResultDTO;
import com.sonograma.dto.VinylFutureManualPreviewDTO;
import com.sonograma.dto.VinylFuturePendingItemDTO;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EnrichStatus;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.ImportStatus;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.exception.ConflictoNegocioException;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.PedidoItemRepository;
import com.sonograma.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VinylFutureManualImportService {

    private static final Duration PREVIEW_TTL = Duration.ofHours(2);

    private final VinylFutureScraperService scraperService;
    private final VinylFutureAssetService assetService;
    private final VinylFutureCatalogStockService catalogStockService;
    private final CatalogPricingService pricingService;
    private final AudioPreviewService audioPreviewService;
    private final PreVentaCodeMatcher preVentaCodeMatcher;
    private final CsvExportService csvExportService;
    private final ZipBundleService zipBundleService;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoRepository pedidoRepository;

    private final Map<String, ManualPreviewSession> previews = new ConcurrentHashMap<>();

    public VinylFutureManualPreviewDTO search(String url, Long pendingItemId) {
        cleanup();
        String normalizedUrl = validateUrl(url);
        PedidoItem pendingItem = pendingItemId == null ? null : requirePendingItem(pendingItemId);
        VinylPageData scraped = scraperService.scrape(normalizedUrl)
            .orElseThrow(() -> new NegocioException(
                "No se pudo obtener información del producto desde ese enlace de Vinyl Future."
            ));
        if (blank(scraped.code())) {
            throw new NegocioException(
                "El producto no contiene un código de catálogo confiable y no puede importarse automáticamente."
            );
        }

        InvoiceItem item = toInvoiceItem(scraped, pendingItem);
        VinylFutureAssetService.AssetStoreResult assets = assetService.storeAssetsWithResult(item, scraped);
        VinylPageData storedPage = assets == null ? scraped : assets.page();
        VinylFutureCatalogStockService.Resolution resolution = catalogStockService.preview(storedPage.code());
        String previewId = UUID.randomUUID().toString();
        ManualPreviewSession session = new ManualPreviewSession(
            previewId, pendingItemId, item, storedPage, Instant.now(), resolution, null
        );
        previews.put(previewId, session);
        return toPreview(session, pendingItem);
    }

    @Transactional
    public VinylFutureManualImportResultDTO confirm(String previewId, Integer requestedQuantity) {
        ManualPreviewSession initial = requireSession(previewId);
        synchronized (initial) {
            ManualPreviewSession session = requireSession(previewId);
            if (session.result() != null) {
                VinylFutureManualImportResultDTO result = session.result();
                return new VinylFutureManualImportResultDTO(
                    result.previewId(), result.productId(), result.catalogueStatus(), 0,
                    result.resultingStock(), result.pendingItemResolved(), true
                );
            }

            int quantity = requestedQuantity == null ? suggestedQuantity(session.pendingItemId()) : requestedQuantity;
            if (quantity < 1) {
                throw new IllegalArgumentException("La cantidad debe ser un número entero mayor que cero.");
            }

            PedidoItem pendingItem = null;
            if (session.pendingItemId() != null) {
                pendingItem = pedidoItemRepository.findByIdForUpdate(session.pendingItemId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Elemento pendiente", session.pendingItemId()));
                if (pendingItem.getDisco() != null || "RESUELTO".equals(pendingItem.getEstadoLectura())) {
                    Disco resolved = pendingItem.getDisco();
                    VinylFutureManualImportResultDTO result = new VinylFutureManualImportResultDTO(
                        previewId,
                        resolved != null ? resolved.getIdDisco() : null,
                        "EXISTENTE",
                        0,
                        resolved != null && resolved.getCantidadCopias() != null ? resolved.getCantidadCopias() : 0,
                        true,
                        true
                    );
                    previews.put(previewId, session.withResult(result));
                    return result;
                }
                assertPendingVinylFuture(pendingItem);
            }

            VinylPageData page = session.page();
            InvoiceItem item = session.item();
            VinylFutureCatalogStockService.Resolution resolution = catalogStockService.addStock(
                page.code(),
                quantity,
                () -> buildNewProduct(item, page, quantity),
                existing -> enrichExisting(existing, item, page)
            );
            Disco disco = resolution.disco();
            audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), page.tracks());
            preVentaCodeMatcher.linkPendingPreSales(disco);

            boolean resolvedPending = false;
            if (pendingItem != null) {
                resolvePendingItem(pendingItem, disco, page, quantity);
                resolvedPending = true;
            }

            VinylFutureManualImportResultDTO result = new VinylFutureManualImportResultDTO(
                previewId,
                disco.getIdDisco(),
                resolution.status() == VinylFutureCatalogStockService.ProductStatus.NEW ? "NUEVO" : "EXISTENTE",
                resolution.addedCopies(),
                resolution.resultingStock(),
                resolvedPending,
                false
            );
            previews.put(previewId, session.withResult(result));
            return result;
        }
    }

    @Transactional(readOnly = true)
    public List<VinylFuturePendingItemDTO> listPendingItems() {
        return pedidoItemRepository.findPendingVinylFutureReviewItems().stream()
            .map(item -> new VinylFuturePendingItemDTO(
                item.getIdPedidoItem(),
                item.getPedido().getIdPedido(),
                item.getPedido().getNumeroFactura(),
                item.getPaginaFuente(),
                item.getTextoFuente(),
                item.getMotivoRevision(),
                item.getCantidadEstimada()
            ))
            .toList();
    }

    public Resource cover(String previewId) throws java.io.IOException {
        VinylPageData page = requireSession(previewId).page();
        String relative = assetService.relativePath(page.frontImageUrl());
        if (relative == null) throw new RecursoNoEncontradoException("Portada no disponible");
        return assetService.load(relative);
    }

    public String coverContentType(String previewId) {
        VinylPageData page = requireSession(previewId).page();
        String relative = assetService.relativePath(page.frontImageUrl());
        return relative == null ? "image/jpeg" : assetService.contentType(relative);
    }

    public Path buildSingleZip(String previewId) throws java.io.IOException {
        ManualPreviewSession session = requireSession(previewId);
        Map<InvoiceItem, Optional<VinylPageData>> pageMap = new LinkedHashMap<>();
        pageMap.put(session.item(), Optional.of(session.page()));
        Map<InvoiceItem, Optional<String>> searchMap = new LinkedHashMap<>();
        searchMap.put(session.item(), Optional.of(session.page().sourceUrl()));
        String csv = csvExportService.buildCsv(searchMap);
        return zipBundleService.buildZip(
            csv,
            pageMap,
            "VinylFuture_Producto_" + sanitizeFilename(session.page().code())
        );
    }

    private VinylFutureManualPreviewDTO toPreview(ManualPreviewSession session, PedidoItem pendingItem) {
        VinylPageData page = session.page();
        boolean complete = !blank(page.artist()) && !blank(page.title()) && !blank(page.code());
        Disco existing = session.initialResolution().disco();
        return new VinylFutureManualPreviewDTO(
            session.previewId(),
            session.pendingItemId(),
            pendingItem == null ? 1 : positiveOrOne(pendingItem.getCantidadEstimada()),
            page.sourceUrl(),
            page.code(),
            page.artist(),
            page.title(),
            page.format(),
            page.label(),
            page.year(),
            page.genre(),
            page.country(),
            page.condition(),
            page.description(),
            page.purchasePrice(),
            page.frontImageUrl(),
            page.tracks() == null ? List.of() : page.tracks(),
            complete ? "Información disponible" : "Información incompleta",
            existing != null,
            existing != null ? existing.getIdDisco() : null,
            assetService.localPath(page.frontImageUrl()) != null
        );
    }

    private Disco buildNewProduct(InvoiceItem item, VinylPageData page, int quantity) {
        BigDecimal cost = firstNonNull(item.precioUnitario(), page.purchasePrice());
        CatalogPricingService.PricingResult pricing = pricingService.calculate(cost, quantity, page.format());
        Disco disco = new Disco();
        disco.setCodigoInterno(page.code());
        disco.setArtista(firstNonBlank(page.artist(), item.artista(), "Desconocido"));
        disco.setAlbum(firstNonBlank(page.title(), item.album(), "Sin título"));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setCondicion(parseCondition(page.condition()));
        disco.setTipoDisco(parseFormat(page.format()));
        disco.setCosto(cost);
        disco.setCostoMoneda("EUR");
        disco.setFormato(page.format());
        disco.setPrecioVenta(pricing == null ? null : pricing.finalPriceUyu());
        disco.setPricingMode(PricingMode.AUTO);
        enrichExisting(disco, item, page);
        return disco;
    }

    private void enrichExisting(Disco disco, InvoiceItem item, VinylPageData page) {
        disco.setArtista(firstNonBlank(disco.getArtista(), page.artist(), item.artista(), "Desconocido"));
        disco.setAlbum(firstNonBlank(disco.getAlbum(), page.title(), item.album(), "Sin título"));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        if (disco.getCondicion() == null) disco.setCondicion(parseCondition(page.condition()));
        if (disco.getTipoDisco() == null) disco.setTipoDisco(parseFormat(page.format()));
        if (blank(disco.getFormato())) disco.setFormato(page.format());
        if (disco.getCosto() == null) disco.setCosto(firstNonNull(item.precioUnitario(), page.purchasePrice()));
        if (blank(disco.getCostoMoneda())) disco.setCostoMoneda("EUR");
        if (blank(disco.getSelloDiscografico())) disco.setSelloDiscografico(page.label());
        if (blank(disco.getGenero())) disco.setGenero(page.genre());
        if (disco.getAnio() == null) disco.setAnio(page.year());
        if (blank(disco.getPais())) disco.setPais(page.country());
        if (blank(disco.getDescripcion())) disco.setDescripcion(page.description());
        if (blank(disco.getImagenUrl()) && !blank(page.frontImageUrl())) disco.setImagenUrl(page.frontImageUrl());
        if (blank(disco.getDiscogsUrl())) disco.setDiscogsUrl(page.sourceUrl());
        if (blank(disco.getTracklist()) && page.tracks() != null && !page.tracks().isEmpty()) {
            disco.setTracklist(page.tracks().stream()
                .map(track -> firstNonBlank(track.label(), "") + " " + firstNonBlank(track.name(), "Track"))
                .map(String::strip)
                .collect(Collectors.joining("\n")));
        }
    }

    private void resolvePendingItem(PedidoItem item, Disco disco, VinylPageData page, int quantity) {
        item.setCodigo(page.code());
        item.setArtista(page.artist());
        item.setTitulo(page.title());
        item.setFormato(page.format());
        item.setCantidad(quantity);
        item.setPortadaUrl(page.frontImageUrl());
        item.setDisco(disco);
        item.setEstadoLectura("RESUELTO");
        item.setMotivoRevision(null);
        item.setEnrichStatus(EnrichStatus.IMPORTED);
        pedidoItemRepository.save(item);

        Pedido pedido = item.getPedido();
        boolean stillPending = pedido.getItems().stream()
            .anyMatch(candidate -> candidate.getDisco() == null);
        pedido.setImportStatus(stillPending ? ImportStatus.PARTIALLY_COMPLETED : ImportStatus.COMPLETED);
        pedidoRepository.save(pedido);
    }

    private PedidoItem requirePendingItem(Long id) {
        PedidoItem item = pedidoItemRepository.findByIdWithPedido(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Elemento pendiente", id));
        assertPendingVinylFuture(item);
        return item;
    }

    private void assertPendingVinylFuture(PedidoItem item) {
        if (item.getPedido() == null || !"vinylfuture".equalsIgnoreCase(item.getPedido().getOrigenImportacion())) {
            throw new NegocioException("El elemento no pertenece a una factura Vinyl Future.");
        }
        if (item.getDisco() != null || !"REVIEW_REQUIRED".equals(item.getEstadoLectura())) {
            throw new ConflictoNegocioException("El elemento pendiente ya fue resuelto.");
        }
    }

    private int suggestedQuantity(Long pendingItemId) {
        if (pendingItemId == null) return 1;
        return pedidoItemRepository.findById(pendingItemId)
            .map(PedidoItem::getCantidadEstimada)
            .map(this::positiveOrOne)
            .orElse(1);
    }

    private InvoiceItem toInvoiceItem(VinylPageData page, PedidoItem pendingItem) {
        BigDecimal price = firstNonNull(
            pendingItem == null ? null : pendingItem.getPrecioUnitarioEur(),
            page.purchasePrice()
        );
        int quantity = pendingItem == null ? 1 : positiveOrOne(pendingItem.getCantidadEstimada());
        return new InvoiceItem(
            page.code(), page.artist(), page.title(), page.format(), price, quantity,
            price == null ? null : price.multiply(BigDecimal.valueOf(quantity))
        );
    }

    private String validateUrl(String rawUrl) {
        if (blank(rawUrl)) throw new IllegalArgumentException("Ingresá un enlace de Vinyl Future.");
        try {
            URI uri = URI.create(rawUrl.strip());
            String host = uri.getHost();
            boolean validHost = host != null
                && (host.equalsIgnoreCase("vinylfuture.com")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".vinylfuture.com"));
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (!validHost || !validScheme || uri.getUserInfo() != null || blank(uri.getPath())) {
                throw new IllegalArgumentException("El enlace no pertenece a Vinyl Future.");
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException ex) {
            if ("El enlace no pertenece a Vinyl Future.".equals(ex.getMessage())) throw ex;
            throw new IllegalArgumentException("El enlace de Vinyl Future no es válido.");
        }
    }

    private ManualPreviewSession requireSession(String previewId) {
        cleanup();
        ManualPreviewSession session = previews.get(previewId);
        if (session == null) {
            throw new RecursoNoEncontradoException("La previsualización Vinyl Future no existe o venció.");
        }
        return session;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(PREVIEW_TTL);
        previews.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private int positiveOrOne(Integer value) {
        return value != null && value > 0 ? value : 1;
    }

    private String sanitizeFilename(String value) {
        String safe = firstNonBlank(value, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        return safe.replaceAll("[/\\\\:*?\"<>|]", "_").replaceAll("\\s+", "_").strip();
    }

    private TipoDisco parseFormat(String value) {
        if (blank(value)) return TipoDisco.VINILO;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("CD")) return TipoDisco.CD;
        if (normalized.contains("CASSETTE") || normalized.contains("TAPE")) return TipoDisco.CASSETTE;
        if (normalized.contains("DIGITAL")) return TipoDisco.DIGITAL;
        return TipoDisco.VINILO;
    }

    private CondicionDisco parseCondition(String value) {
        if (blank(value)) return CondicionDisco.NUEVO;
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("USED") || normalized.contains("USADO")
            ? CondicionDisco.USADO : CondicionDisco.NUEVO;
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) if (value != null) return value;
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ManualPreviewSession(
        String previewId,
        Long pendingItemId,
        InvoiceItem item,
        VinylPageData page,
        Instant createdAt,
        VinylFutureCatalogStockService.Resolution initialResolution,
        VinylFutureManualImportResultDTO result
    ) {
        ManualPreviewSession withResult(VinylFutureManualImportResultDTO value) {
            return new ManualPreviewSession(
                previewId, pendingItemId, item, page, createdAt, initialResolution, value
            );
        }
    }
}
