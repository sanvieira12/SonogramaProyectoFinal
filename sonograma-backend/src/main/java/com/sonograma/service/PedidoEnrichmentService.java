package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.PedidoItem;
import com.sonograma.enums.EnrichStatus;
import com.sonograma.repository.PedidoItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoEnrichmentService {

    private final PedidoItemRepository itemRepository;
    private final VinylFutureSearchService searchService;
    private final VinylFutureScraperService scraperService;

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

    @Transactional
    public void marcarPedidoPostEnriquecimiento(Long pedidoId,
            com.sonograma.repository.PedidoRepository pedidoRepository) {
        pedidoRepository.findById(pedidoId).ifPresent(pedido -> {
            boolean anyFailed = pedido.getItems().stream()
                .anyMatch(i -> i.getEnrichStatus() == EnrichStatus.FAILED);
            boolean anyPending = pedido.getItems().stream()
                .anyMatch(i -> i.getEnrichStatus() == EnrichStatus.PENDING);
            if (anyPending) {
                pedido.setImportStatus(com.sonograma.enums.ImportStatus.PARTIALLY_COMPLETED);
            } else if (anyFailed) {
                pedido.setImportStatus(com.sonograma.enums.ImportStatus.PARTIALLY_COMPLETED);
            } else {
                pedido.setImportStatus(com.sonograma.enums.ImportStatus.AWAITING_REVIEW);
            }
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
