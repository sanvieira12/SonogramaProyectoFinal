package com.sonograma.service;

import com.sonograma.service.importacion.DiscogsCoverService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogCleanupService {

    private final EntityManager entityManager;
    private final VinylFutureAssetService vinylFutureAssetService;
    private final DiscogsCoverService discogsCoverService;
    private final VinylFutureImportBatchService importBatchService;

    @Transactional(readOnly = true)
    public CatalogCleanupResult preview(CleanupScope scope) {
        return buildResult(scope, false);
    }

    @Transactional
    public CatalogCleanupResult execute(CleanupScope scope) {
        CatalogCleanupResult result = buildResult(scope, true);
        if (result.targetDiscos() == 0) {
            log.info("Catalog cleanup ejecutado sin discos objetivo para scope={}", scope.name());
            cleanupTransientArtifacts(scope, result.counts());
            return result;
        }

        update("UPDATE detalle_venta SET id_disco = NULL WHERE id_disco IN (:ids)", result.targetIds());
        update("UPDATE venta SET id_disco = NULL WHERE id_disco IN (:ids)", result.targetIds());
        update("UPDATE pre_venta SET id_disco = NULL WHERE id_disco IN (:ids)", result.targetIds());
        delete("DELETE FROM reserva WHERE id_disco IN (:ids)", result.targetIds());
        update("UPDATE pedido_item SET id_disco = NULL WHERE id_disco IN (:ids)", result.targetIds());
        update("UPDATE shipping_order_item SET id_disco = NULL WHERE id_disco IN (:ids)", result.targetIds());
        update("UPDATE discogs_import_row SET imported_catalog_product_id = NULL WHERE imported_catalog_product_id IN (:ids)", result.targetIds());
        delete("DELETE FROM catalog_audio_preview WHERE id_disco IN (:ids)", result.targetIds());
        delete("DELETE FROM disco_qr_copy WHERE id_disco IN (:ids)", result.targetIds());
        delete("DELETE FROM movimiento_stock WHERE id_disco IN (:ids)", result.targetIds());
        delete("DELETE FROM disco WHERE id_disco IN (:ids)", result.targetIds());

        if (scope == CleanupScope.ALL_CATALOG) {
            deleteWithoutIds("DELETE FROM discogs_import_row");
            deleteWithoutIds("DELETE FROM discogs_import_job");
        }

        cleanupTransientArtifacts(scope, result.counts());
        logCounts("Catalog cleanup", scope, result.counts());
        return result;
    }

    private CatalogCleanupResult buildResult(CleanupScope scope, boolean execute) {
        List<Long> targetIds = findTargetIds(scope);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("discos", targetIds.size());
        counts.put("detalleVentaDesvincular", count("SELECT COUNT(*) FROM detalle_venta WHERE id_disco IN (:ids)", targetIds));
        counts.put("ventaDesvincular", count("SELECT COUNT(*) FROM venta WHERE id_disco IN (:ids)", targetIds));
        counts.put("preVentasDesvincular", count("SELECT COUNT(*) FROM pre_venta WHERE id_disco IN (:ids)", targetIds));
        counts.put("reservasBorrar", count("SELECT COUNT(*) FROM reserva WHERE id_disco IN (:ids)", targetIds));
        counts.put("pedidoItemsDesvincular", count("SELECT COUNT(*) FROM pedido_item WHERE id_disco IN (:ids)", targetIds));
        counts.put("shippingItemsDesvincular", count("SELECT COUNT(*) FROM shipping_order_item WHERE id_disco IN (:ids)", targetIds));
        counts.put("discogsRowsDesvincular", count("SELECT COUNT(*) FROM discogs_import_row WHERE imported_catalog_product_id IN (:ids)", targetIds));
        counts.put("audioPreviewsBorrar", count("SELECT COUNT(*) FROM catalog_audio_preview WHERE id_disco IN (:ids)", targetIds));
        counts.put("qrCopiesBorrar", count("SELECT COUNT(*) FROM disco_qr_copy WHERE id_disco IN (:ids)", targetIds));
        counts.put("movimientosStockBorrar", count("SELECT COUNT(*) FROM movimiento_stock WHERE id_disco IN (:ids)", targetIds));
        if (scope == CleanupScope.ALL_CATALOG) {
            counts.put("discogsJobsBorrar", count("SELECT COUNT(*) FROM discogs_import_job"));
            counts.put("discogsRowsBorrar", count("SELECT COUNT(*) FROM discogs_import_row"));
        }
        return new CatalogCleanupResult(scope, execute, targetIds.size(), targetIds, counts);
    }

    private void cleanupTransientArtifacts(CleanupScope scope, Map<String, Integer> counts) {
        counts.put("vinylfutureBatchesLimpiar", importBatchService.clearAll());
        counts.put("vinylfutureAssetsBorrar", vinylFutureAssetService.clearStoredAssets());
        if (scope == CleanupScope.ALL_CATALOG) {
            counts.put("discogsCoversBorrar", discogsCoverService.clearStoredCovers());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> findTargetIds(CleanupScope scope) {
        String sql = """
            SELECT d.id_disco
            FROM disco d
            WHERE (:vinylFutureOnly = false OR UPPER(COALESCE(d.procedencia, '')) = 'VINYL_FUTURE')
            ORDER BY d.id_disco
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("vinylFutureOnly", scope == CleanupScope.VINYL_FUTURE_ONLY);
        return ((List<Number>) query.getResultList()).stream().map(Number::longValue).toList();
    }

    private int count(String sql, List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        Number value = (Number) query.getSingleResult();
        return value == null ? 0 : value.intValue();
    }

    private int count(String sql) {
        Number value = (Number) entityManager.createNativeQuery(sql).getSingleResult();
        return value == null ? 0 : value.intValue();
    }

    private void update(String sql, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        query.executeUpdate();
    }

    private void delete(String sql, List<Long> ids) {
        update(sql, ids);
    }

    private void deleteWithoutIds(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private void logCounts(String prefix, CleanupScope scope, Map<String, Integer> counts) {
        counts.forEach((key, value) ->
            log.info("{} scope={} {}={}", prefix, scope.name().toLowerCase(Locale.ROOT), key, value)
        );
    }

    public enum CleanupScope {
        ALL_CATALOG,
        VINYL_FUTURE_ONLY;

        public static CleanupScope from(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL_CATALOG;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "VINYL_FUTURE", "VINYL_FUTURE_ONLY" -> VINYL_FUTURE_ONLY;
                default -> ALL_CATALOG;
            };
        }
    }

    public record CatalogCleanupResult(
        CleanupScope scope,
        boolean execute,
        int targetDiscos,
        List<Long> targetIds,
        Map<String, Integer> counts
    ) {}
}
