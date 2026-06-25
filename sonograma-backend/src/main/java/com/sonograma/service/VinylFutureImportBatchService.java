package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.VinylPageData;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VinylFutureImportBatchService {

    private static final Duration TTL = Duration.ofHours(6);
    private static final int MAX_BATCHES = 20;

    private final Map<String, ImportBatch> batches = new ConcurrentHashMap<>();

    public String store(
            String csv,
            Map<InvoiceItem, Optional<VinylPageData>> pageDataMap) {
        cleanup();
        String importId = UUID.randomUUID().toString();
        batches.put(importId, new ImportBatch(
            importId,
            csv,
            new LinkedHashMap<>(pageDataMap),
            Instant.now()
        ));
        cleanupOverflow();
        return importId;
    }

    public Optional<ImportBatch> find(String importId) {
        cleanup();
        return Optional.ofNullable(batches.get(importId));
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(TTL);
        batches.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private void cleanupOverflow() {
        if (batches.size() <= MAX_BATCHES) return;
        Iterator<ImportBatch> oldest = batches.values().stream()
            .sorted((first, second) -> first.createdAt().compareTo(second.createdAt()))
            .iterator();
        while (batches.size() > MAX_BATCHES && oldest.hasNext()) {
            batches.remove(oldest.next().importId());
        }
    }

    public record ImportBatch(
        String importId,
        String csv,
        Map<InvoiceItem, Optional<VinylPageData>> pageDataMap,
        Instant createdAt
    ) {}
}
