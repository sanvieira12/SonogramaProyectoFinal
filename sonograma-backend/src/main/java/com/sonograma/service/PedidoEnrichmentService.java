package com.sonograma.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.EnrichStatus;
import com.sonograma.repository.PedidoItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoEnrichmentService {

    private final PedidoItemRepository itemRepository;
    private final VinylFutureSearchService searchService;
    private final VinylFutureScraperService scraperService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void procesarItem(Long itemId) {
        PedidoItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) return;

        try {
            InvoiceItem invoiceItem = toInvoiceItem(item);
            Optional<String> url = searchService.buscar(invoiceItem);
            if (url.isPresent()) {
                Optional<VinylPageData> pageData = scraperService.scrape(url.get());
                pageData.ifPresent(d -> {
                    if (d.frontImageUrl() != null && !d.frontImageUrl().isBlank()) {
                        item.setPortadaUrl(d.frontImageUrl());
                    }
                    serializarTracks(item, d.tracks());
                });
            }
            item.setEnrichStatus(EnrichStatus.ENRICHED);
            log.debug("Item {} enriquecido: {} - {}", itemId, item.getArtista(), item.getTitulo());
        } catch (Exception e) {
            item.setEnrichStatus(EnrichStatus.FAILED);
            log.warn("Error enriqueciendo item {}: {}", itemId, e.getMessage());
        }
        itemRepository.save(item);
    }

    private void serializarTracks(PedidoItem item, List<TrackInfo> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        try {
            item.setTracksJson(objectMapper.writeValueAsString(tracks));
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar tracks para item {}: {}", item.getIdPedidoItem(), e.getMessage());
        }
    }

    /** Parses the stored JSON back to a list of TrackInfo, returns empty on any error. */
    public List<TrackInfo> deserializarTracks(String tracksJson) {
        if (tracksJson == null || tracksJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                tracksJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TrackInfo.class)
            );
        } catch (JsonProcessingException e) {
            log.warn("No se pudo deserializar tracks JSON: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public void marcarPedidoPostEnriquecimiento(Long pedidoId,
            com.sonograma.repository.PedidoRepository pedidoRepository) {
        pedidoRepository.findById(pedidoId).ifPresent(pedido -> {
            boolean anyFailed = pedido.getItems().stream()
                .anyMatch(i -> i.getEnrichStatus() == EnrichStatus.FAILED);
            boolean anyPending = pedido.getItems().stream()
                .anyMatch(i -> i.getEnrichStatus() == EnrichStatus.PENDING);
            com.sonograma.enums.ImportStatus newStatus;
            if (anyPending || anyFailed) {
                newStatus = com.sonograma.enums.ImportStatus.PARTIALLY_COMPLETED;
            } else {
                newStatus = com.sonograma.enums.ImportStatus.AWAITING_REVIEW;
            }
            pedido.setImportStatus(newStatus);
            pedidoRepository.save(pedido);
        });
    }

    private InvoiceItem toInvoiceItem(PedidoItem item) {
        return new InvoiceItem(
            item.getCodigo(),
            item.getArtista(),
            item.getTitulo(),
            item.getFormato(),
            item.getPrecioUnitarioEur(),
            item.getCantidad(),
            item.getTotalLineaEur()
        );
    }
}
